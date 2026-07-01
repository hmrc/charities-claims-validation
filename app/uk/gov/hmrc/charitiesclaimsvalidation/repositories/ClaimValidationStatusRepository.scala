/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.repositories

import com.mongodb.MongoWriteException
import org.bson.BsonDateTime
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.model.Filters.*
import org.mongodb.scala.model.IndexModel
import org.mongodb.scala.model.Updates.{combine, currentDate, set}
import play.api.Logging
import play.api.libs.json.Json
import uk.gov.hmrc.charitiesclaimsvalidation.config.AppConfig
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.{ClaimValidationStatus, ClaimValidationSummary, FileStatus, ValidationType}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ClaimValidationStatusRepository @Inject() (
  mongoComponent: MongoComponent,
  config: AppConfig
)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[ClaimValidationStatus](
      mongoComponent = mongoComponent,
      collectionName = "claim-validation-status",
      domainFormat = ClaimValidationStatus.format,
      indexes = ClaimValidationStatusRepository.indexes(config.ttl, config.isTtlEnabled),
      replaceIndexes = config.replaceIndexes
    )
    with Logging {

  override lazy val requiresTtlIndex: Boolean = config.isTtlEnabled
  private val DuplicateKeyError               = 11000

  private def compoundId(claimId: String, reference: String) =
    BsonDocument("claimId" -> claimId, "reference" -> reference)

  private def logAndFail[T](msg: => String): PartialFunction[Throwable, Future[T]] = { case ex =>
    logger.error(msg, ex)
    Future.failed(ex)
  }

  private def retry[T](operationName: String)(operation: => Future[T]): Future[T] =
    MongoRetry.withRetry(operationName, config.retryMaxAttempts, config.retryInitialDelay, config.retryMaxDelay)(
      operation
    )

  def insert(status: ClaimValidationStatus): Future[Boolean] =
    retry("insert") {
      collection
        .insertOne(status)
        .toFuture()
        .map { result =>
          logger.info(s"Inserting row to collection $collectionName with claimId:${status.claimId}")
          result.wasAcknowledged()
        }
    }.recoverWith {
      case ex: MongoWriteException if ex.getError.getCode == DuplicateKeyError =>
        logger.warn(s"Duplicate insert: Document already exists for claimId=${status.claimId}, reference=${status.reference}")
        Future.successful(false)
      case ex: Exception =>
        logger.error(
          s"Failed to insert validation status for claimId=${status.claimId}, reference=${status.reference}: ${ex.getMessage}",
          ex
        )
        Future.failed(ex)
    }

  def findByReference(claimId: String, reference: String): Future[Option[ClaimValidationStatus]] =
    collection
      .find(equal("_id", compoundId(claimId, reference)))
      .headOption()
      .recoverWith(logAndFail(s"Failed to find validation status by reference for claimId=$claimId, reference=$reference ."))

  def update(claimId: String, reference: String, newStatus: ClaimValidationStatus): Future[Boolean] = {
    val compoundId = BsonDocument("claimId" -> claimId, "reference" -> reference)
    retry("update") {
      collection
        .replaceOne(equal("_id", compoundId), newStatus)
        .toFuture()
        .map(result => result.getMatchedCount > 0)
    }.recoverWith { case ex: Exception =>
      logger.error(s"Failed to update validation status for claimId=$claimId, reference=$reference: ${ex.getMessage}", ex)
      Future.failed(ex)
    }
  }

  def getClaimSummary(claimId: String): Future[Option[ClaimValidationSummary]] =
    findByClaimId(claimId).map { uploads =>
      if (uploads.nonEmpty) Some(ClaimValidationSummary(claimId, uploads))
      else None
    }

  def findByClaimId(claimId: String): Future[Seq[ClaimValidationStatus]] =
    collection
      .find(equal("_id.claimId", claimId))
      .toFuture()
      .map(_.toSeq)
      .recoverWith(logAndFail(s"Failed to find validation statuses by claimId=$claimId ."))

  def findByClaimIdAndValidationType(claimId: String, validationType: ValidationType): Future[Option[ClaimValidationStatus]] =
    collection
      .find(
        and(
          equal("_id.claimId", claimId),
          equal("validationType", Json.toJson(validationType).as[String])
        )
      )
      .headOption()
      .recoverWith(
        logAndFail(
          s"Failed to find validation status for claimId=$claimId, validationType=${validationType.toString}."
        )
      )

  def deleteByReference(claimId: String, reference: String): Future[Boolean] =
    retry("deleteByReference") {
      collection
        .deleteOne(equal("_id", compoundId(claimId, reference)))
        .head()
        .map(_.getDeletedCount > 0)
    }.recoverWith(logAndFail(s"ClaimValidationStatus delete failed for claimId=$claimId, reference=$reference ."))

  def updateFileStatusIfCurrentStatusMatches(
    claimId: String,
    reference: String,
    existingStatus: FileStatus,
    requestedStatus: FileStatus
  ): Future[Boolean] = {
    if (existingStatus == requestedStatus) {
      Future.successful(false)
    } else {
      val filter = and(equal("_id", compoundId(claimId, reference)), equal("fileStatus", existingStatus.value))
      val update = combine(
        set("fileStatus", requestedStatus.value),
        set("updatedAt", new BsonDateTime(Instant.now().toEpochMilli))
      )
      retry("updateFileStatus") {
        collection
          .updateOne(filter, update)
          .toFuture()
          .map(result => result.getModifiedCount > 0)
      }.recoverWith(
        logAndFail(
          s"Failed to update file status $existingStatus to $requestedStatus by " +
            s"claimId=$claimId reference=$reference"
        )
      )
    }
  }

  def deleteByClaimId(claimId: String): Future[Boolean] =
    retry("deleteByClaimId") {
      collection
        .deleteMany(equal("_id.claimId", claimId))
        .toFuture()
        .map(_ => true)
    }.recoverWith(logAndFail(s"Failed to delete validation statuses by claimId=$claimId"))

  def touchTtlByClaimId(claimId: String): Future[Unit] =
    retry("touchTtlByClaimId") {
      collection
        .updateMany(
          equal("_id.claimId", claimId),
          currentDate("updatedAt")
        )
        .toFuture()
        .map(_ => ())
    }.recover { case ex =>
      logger.warn(s"Failed to touch validation status TTL for claimId=$claimId", ex)
      ()
    }
}

object ClaimValidationStatusRepository {

  import org.mongodb.scala.model.IndexOptions
  import org.mongodb.scala.model.Indexes.{ascending, compoundIndex}

  def indexes(ttlSeconds: Int, isTtlEnabled: Boolean): Seq[IndexModel] = {
    // index claimId as we can query by it, and it's a partial lookup (_id is compound id with claimId and reference)
    val claimIdIndex = IndexModel(
      keys = ascending("_id.claimId"),
      indexOptions = IndexOptions().name("claimId-index")
    )

    // unique index on claimId + validationType to prevent duplicate validation types for the same claim
    val claimValidationTypeIndex = IndexModel(
      keys = compoundIndex(ascending("_id.claimId"), ascending("validationType")),
      indexOptions = IndexOptions().name("claimId-validationType-unique-index").unique(true)
    )

    val baseIndexes = Seq(claimIdIndex, claimValidationTypeIndex)

    if isTtlEnabled then
      val ttlIndex = IndexModel(
        keys = ascending("updatedAt"),
        indexOptions = IndexOptions()
          .name("updatedAt-ttl-index")
          .expireAfter(ttlSeconds.toLong, java.util.concurrent.TimeUnit.SECONDS)
      )
      baseIndexes :+ ttlIndex
    else baseIndexes
  }
}
