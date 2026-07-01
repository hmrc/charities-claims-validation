/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.repositories

import org.mockito.Mockito.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.charitiesclaimsvalidation.config.AppConfig
import uk.gov.hmrc.charitiesclaimsvalidation.models.*
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.*
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.FileStatus.{AwaitingUpload, Verifying}
import uk.gov.hmrc.mongo.test.MongoSupport

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}

class ClaimValidationStatusRepositorySpec
    extends AnyWordSpec
    with Matchers
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with MongoSupport
    with MockitoSugar:

  private val mockConfig = mock[AppConfig]
  when(mockConfig.replaceIndexes).thenReturn(true)
  when(mockConfig.ttl).thenReturn(2592000)
  when(mockConfig.isTtlEnabled).thenReturn(true)
  when(mockConfig.retryMaxAttempts).thenReturn(3)
  when(mockConfig.retryInitialDelay).thenReturn(50.millis)
  when(mockConfig.retryMaxDelay).thenReturn(500.millis)

  private val repository   = new ClaimValidationStatusRepository(mongoComponent, mockConfig)
  private val claimId      = "test-claim-123"
  private val otherClaimId = "test-claim-124"
  private val ref1         = "ref-uuid-001"
  private val ref2         = "ref-uuid-002"
  private val timestamp    = Instant.parse("2025-01-01T12:00:00Z")
  private val someFields   = Some(Map("foo" -> "bar"))
  private val validationStatus1 = AwaitingUploadStatus(
    claimId = claimId,
    reference = ref1,
    validationType = ValidationType.GiftAid,
    uploadUrl = "https://test-url-1",
    initiateTimestamp = timestamp,
    fields = someFields,
    createdAt = timestamp,
    updatedAt = timestamp
  )
  private val validationStatus2 = VerifyingStatus(
    claimId = claimId,
    reference = ref2,
    validationType = ValidationType.OtherIncome,
    createdAt = timestamp,
    updatedAt = timestamp
  )

  private val validationStatus3 = VerifyingStatus(
    claimId = otherClaimId,
    reference = ref2,
    validationType = ValidationType.CommunityBuildings,
    createdAt = timestamp,
    updatedAt = timestamp
  )

  override def beforeEach(): Unit =
    super.beforeEach()
    dropDatabase()
    await(repository.ensureIndexes())

  override def afterAll(): Unit =
    dropDatabase()

  private def await[T](future: Future[T]): T = Await.result(future, 5.seconds)

  "indexes" should:
    "create claimId index, unique claimId-validationType index, and TTL index" in:
      import org.mongodb.scala.model.Indexes.{ascending, compoundIndex}

      val indexes = repository.indexes

      indexes should have size 3

      val claimIdIndex = indexes.find(_.getOptions.getName == "claimId-index").get
      claimIdIndex.getKeys shouldBe ascending("_id.claimId")

      val uniqueIndex = indexes.find(_.getOptions.getName == "claimId-validationType-unique-index").get
      uniqueIndex.getKeys shouldBe compoundIndex(ascending("_id.claimId"), ascending("validationType"))
      uniqueIndex.getOptions.isUnique shouldBe true

      val ttlIndex = indexes.find(_.getOptions.getName == "updatedAt-ttl-index").get
      ttlIndex.getKeys shouldBe ascending("updatedAt")
      ttlIndex.getOptions.getExpireAfter(java.util.concurrent.TimeUnit.SECONDS) shouldBe 2592000L

    "not include TTL index when isTtlEnabled is false" in:
      val indexesWithoutTtl = ClaimValidationStatusRepository.indexes(ttlSeconds = 2592000, isTtlEnabled = false)

      indexesWithoutTtl should have size 2
      indexesWithoutTtl.find(_.getOptions.getName == "updatedAt-ttl-index") shouldBe None

  "insert" should:
    "successfully insert a document with compound _id" in:
      val result = await(repository.insert(validationStatus1))

      result shouldBe true

      val found = await(repository.findByReference(claimId, ref1))
      found shouldBe defined
      found.get.claimId shouldBe claimId
      found.get.reference shouldBe ref1
      found.get.validationType shouldBe ValidationType.GiftAid

    "allow multiple validation statuses for the same claim" in:
      await(repository.insert(validationStatus1))
      await(repository.insert(validationStatus2))

      val statuses = await(repository.findByClaimId(claimId))

      statuses should have size 2
      statuses.map(_.reference) should contain allOf (ref1, ref2)

    "handle duplicate insert idempotently (return false on duplicate)" in:
      // The first insert should succeed
      await(repository.insert(validationStatus1)) shouldBe true

      // Duplicate insert should return false (idempotent)
      await(repository.insert(validationStatus1)) shouldBe false

      // Verify only one document exists
      await(repository.findByClaimId(claimId)) should have size 1

    "reject insert with same claimId and validationType but different reference" in:
      await(repository.insert(validationStatus1)) shouldBe true

      val duplicateValidationType = AwaitingUploadStatus(
        claimId = claimId,
        reference = "different-ref",
        validationType = ValidationType.GiftAid, // same validationType as validationStatus1
        uploadUrl = "https://test-url-duplicate",
        initiateTimestamp = timestamp,
        fields = someFields,
        createdAt = timestamp,
        updatedAt = timestamp
      )

      // Should reject due to unique index on claimId + validationType
      await(repository.insert(duplicateValidationType)) shouldBe false

      // Verify only original document exists
      await(repository.findByClaimId(claimId)) should have size 1

  "findByReference" should:
    "return None when document does not exist" in:
      val result = await(repository.findByReference("non-existent", "no-ref"))

      result shouldBe None

    "return document when it exists" in:
      await(repository.insert(validationStatus1))

      val result = await(repository.findByReference(claimId, ref1))

      result shouldBe defined
      result.get.claimId shouldBe claimId
      result.get.reference shouldBe ref1
      result.get.fileStatus shouldBe "AWAITING_UPLOAD"

    "return None when claimId matches but reference does not" in:
      await(repository.insert(validationStatus1))

      val result = await(repository.findByReference(claimId, "wrong-reference"))

      result shouldBe None

  "findByClaimId" should:
    "return empty Seq when no documents exist" in:
      val result = await(repository.findByClaimId("non-existent"))

      result shouldBe empty

    "return single document when one exists" in:
      await(repository.insert(validationStatus1))

      val result = await(repository.findByClaimId(claimId))

      result should have size 1
      result.head.claimId shouldBe claimId
      result.head.reference shouldBe ref1

    "return multiple documents for the same claim" in:
      await(repository.insert(validationStatus1))
      await(repository.insert(validationStatus2))

      val result = await(repository.findByClaimId(claimId))

      result should have size 2
      result.map(_.reference) should contain allOf (ref1, ref2)
      result.map(_.validationType) should contain allOf (ValidationType.GiftAid, ValidationType.OtherIncome)

  "findByClaimIdAndValidationType" should:
    "return None when no documents exist for the claimId" in:
      val result = await(repository.findByClaimIdAndValidationType("non-existent", ValidationType.GiftAid))
      result shouldBe None

    "return the matching document when claimId and validationType match" in:
      await(repository.insert(validationStatus1))
      await(repository.insert(validationStatus2))

      val result = await(repository.findByClaimIdAndValidationType(claimId, ValidationType.GiftAid))

      result shouldBe defined
      result.get.claimId shouldBe claimId
      result.get.reference shouldBe ref1
      result.get.validationType shouldBe ValidationType.GiftAid

    "return None when claimId matches but validationType does not" in:
      await(repository.insert(validationStatus1))

      val result = await(repository.findByClaimIdAndValidationType(claimId, ValidationType.OtherIncome))

      result shouldBe None

    "return None when validationType exists but for a different claimId" in:
      await(repository.insert(validationStatus1))
      await(repository.insert(validationStatus3))

      val result = await(repository.findByClaimIdAndValidationType(otherClaimId, ValidationType.GiftAid))

      result shouldBe None

  "getClaimSummary" should:
    "return None when no uploads exist for claim" in:
      val result = await(repository.getClaimSummary("non-existent"))

      result shouldBe None

    "return summary with single upload" in:
      await(repository.insert(validationStatus1))

      val result = await(repository.getClaimSummary(claimId))

      result shouldBe defined
      result.get.claimId shouldBe claimId
      result.get.uploads should have size 1
      result.get.uploads.head.reference shouldBe ref1

    "return summary with multiple uploads grouped by claim" in:
      await(repository.insert(validationStatus1))
      await(repository.insert(validationStatus2))

      val result = await(repository.getClaimSummary(claimId))

      result shouldBe defined
      result.get.claimId shouldBe claimId
      result.get.uploads should have size 2
      result.get.uploads.map(_.reference) should contain allOf (ref1, ref2)

  "deleteByReference" should:
    "return true when deletedCount > 0" in:
      await(repository.insert(validationStatus1))

      val result = await(repository.deleteByReference(validationStatus1.claimId, validationStatus1.reference))
      result shouldBe true

    "return false when deletedCount <= 0" in:
      await(repository.insert(validationStatus1))

      val result = await(repository.deleteByReference(validationStatus2.claimId, validationStatus2.reference))
      result shouldBe false

  "update" should:
    "successfully update an existing document" in:
      await(repository.insert(validationStatus1))

      val updatedStatus = ValidatingStatus(
        claimId = claimId,
        reference = ref1,
        validationType = ValidationType.GiftAid,
        createdAt = timestamp,
        updatedAt = timestamp
      )

      val result = await(repository.update(claimId, ref1, updatedStatus))
      result shouldBe true

      val found = await(repository.findByReference(claimId, ref1))
      found shouldBe defined
      found.get shouldBe a[ValidatingStatus]

    "return true when updating to VerificationFailedStatus" in:
      await(repository.insert(validationStatus2))

      val failedStatus = VerificationFailedStatus(
        claimId = claimId,
        reference = ref2,
        validationType = ValidationType.OtherIncome,
        createdAt = timestamp,
        updatedAt = timestamp,
        failureDetails = FailureDetails(FailureReason.Quarantine, "Virus detected")
      )

      val result = await(repository.update(claimId, ref2, failedStatus))
      result shouldBe true

      val found = await(repository.findByReference(claimId, ref2))
      found shouldBe defined
      found.get shouldBe a[VerificationFailedStatus]
      val failed = found.get.asInstanceOf[VerificationFailedStatus]
      failed.failureDetails.failureReason shouldBe FailureReason.Quarantine

    "return false when document does not exist" in:
      val newStatus = ValidatingStatus(
        claimId = "non-existent",
        reference = "no-ref",
        validationType = ValidationType.GiftAid,
        createdAt = timestamp,
        updatedAt = timestamp
      )

      val result = await(repository.update("non-existent", "no-ref", newStatus))
      result shouldBe false

    "deleteByClaimId" should:
      "return success true when no documents exists for a claim Id" in:
        val result: Boolean = await(repository.deleteByClaimId("non-existent"))

        result shouldBe true

      "delete only the document with the matching claim Id and return success" in:
        await(repository.insert(validationStatus1))
        await(repository.insert(validationStatus3))

        val result: Boolean = await(repository.deleteByClaimId(claimId))
        result shouldBe true

        val deletedDoc = await(repository.findByClaimId(claimId))
        deletedDoc shouldBe empty

        val remainingDoc = await(repository.findByClaimId(otherClaimId))
        remainingDoc should have size 1
        remainingDoc.map(_.claimId) shouldBe Seq(otherClaimId)

      "delete multiple documents for the same claim Id and return success" in:
        await(repository.insert(validationStatus1))
        await(repository.insert(validationStatus2))

        val result: Boolean = await(repository.deleteByClaimId(claimId))
        result shouldBe true

        val remainingDoc = await(repository.findByClaimId(claimId))
        remainingDoc shouldBe empty

  "updateFileStatusIfCurrentStatusMatches" should:
    s"return true and update fileStatus when a matching document exists and the status is ${FileStatus.AwaitingUpload.value} " in:
      await(repository.insert(validationStatus1))

      val result = await(repository.updateFileStatusIfCurrentStatusMatches(claimId, ref1, AwaitingUpload, Verifying))

      result shouldBe true

      val updatedDoc = await(repository.findByReference(claimId, ref1))
      updatedDoc.map(_.fileStatus) shouldBe Some(FileStatus.Verifying.value)
      updatedDoc.map(_.updatedAt.isAfter(timestamp)) shouldBe Some(true)

    s"return false when the status is already ${FileStatus.Verifying.value} and no update" in:
      await(repository.insert(validationStatus2))
      val result = await(repository.updateFileStatusIfCurrentStatusMatches(claimId, ref2, Verifying, Verifying))

      result shouldBe false

    s"return false when the status is ${FileStatus.VerificationFailed.value} and no update" in:
      await(repository.insert(validationStatus2))
      val result = await(repository.updateFileStatusIfCurrentStatusMatches(claimId, ref2, FileStatus.VerificationFailed, Verifying))

      result shouldBe false

    "return false when no document matches the filter " in:
      val result = await(repository.updateFileStatusIfCurrentStatusMatches(claimId, "non-existing-ref ", AwaitingUpload, Verifying))

      result shouldBe false

  "touchTtlByClaimId" should:

    "update updatedAt when documents exist for the claimId" in:
      await(repository.insert(validationStatus1))
      await(repository.insert(validationStatus2))

      await(repository.touchTtlByClaimId(claimId)) // no result to assert

      val updatedTimestamps =
        await(repository.findByClaimId(claimId)).map(_.updatedAt)

      updatedTimestamps.foreach(_.isAfter(timestamp) shouldBe true)

    "not fail when no documents exist for the claimId" in:
      noException shouldBe thrownBy {
        await(repository.touchTtlByClaimId("non-existent-claim"))
      }

    "only update documents for the given claimId" in:
      await(repository.insert(validationStatus1))
      await(repository.insert(validationStatus3)) // different claimId

      await(repository.touchTtlByClaimId(claimId))

      val updatedDocs   = await(repository.findByClaimId(claimId))
      val untouchedDocs = await(repository.findByClaimId(otherClaimId))

      updatedDocs.foreach(_.updatedAt.isAfter(timestamp) shouldBe true)
      untouchedDocs.foreach(_.updatedAt shouldBe timestamp)
