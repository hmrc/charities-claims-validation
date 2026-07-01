/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.services

import eu.timepit.refined.auto.autoUnwrap
import eu.timepit.refined.types.all.NonEmptyString
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.*
import org.scalactic.Prettifier.default
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.mockito.ArgumentCaptor
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.*
import uk.gov.hmrc.charitiesclaimsvalidation.models.*
import uk.gov.hmrc.charitiesclaimsvalidation.models.responses.{UploadDetails, UpscanFailureRequest, UpscanSuccessRequest}
import uk.gov.hmrc.charitiesclaimsvalidation.repositories.ClaimValidationStatusRepository
import uk.gov.hmrc.charitiesclaimsvalidation.services.documentvalidation.{CommonFileValidation, CommunityBuildingValidationService, ConnectedCharitiesValidationService, GiftAidValidationService, OtherIncomeValidationService}

import java.time.LocalDate
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

class UpscanCallbackServiceSpec extends AnyWordSpec with Matchers with MockitoSugar with ScalaFutures with IntegrationPatience {

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  private val claimId                   = "test-claim-123"
  private val reference: NonEmptyString = NonEmptyString.unsafeFrom("11370e18-6e24-453e-b45a-76d3e32ea33d")
  private val timestamp                 = Instant.parse("2018-04-24T09:30:00Z")
  private val fileName                  = NonEmptyString.unsafeFrom("test.pdf")
  private val applicationPdfMimeType    = NonEmptyString.unsafeFrom("application/pdf")
  private val odsMimeType               = NonEmptyString.unsafeFrom("application/vnd.oasis.opendocument.spreadsheet")
  private val checksum                  = NonEmptyString.unsafeFrom("abc123")
  private val downloadUrl               = NonEmptyString.unsafeFrom("https://test.com/file")
  private val fileStatusReady           = NonEmptyString.unsafeFrom("READY")
  private val fileStatusFailed          = NonEmptyString.unsafeFrom("FAILED")

  def createService() = {
    val mockRepository                          = mock[ClaimValidationStatusRepository]
    val mockCommonFileValidation                = mock[CommonFileValidation]
    val mockOtherIncomeValidationService        = mock[OtherIncomeValidationService]
    val mockConnectedCharitiesValidationService = mock[ConnectedCharitiesValidationService]
    val mockCommunityBuildingValidationService  = mock[CommunityBuildingValidationService]
    val mockGiftAidValidationService            = mock[GiftAidValidationService]
    val service = new UpscanCallbackService(
      mockCommonFileValidation,
      mockOtherIncomeValidationService,
      mockConnectedCharitiesValidationService,
      mockCommunityBuildingValidationService,
      mockGiftAidValidationService,
      mockRepository
    )

    (
      service,
      mockRepository,
      mockCommonFileValidation,
      mockOtherIncomeValidationService,
      mockConnectedCharitiesValidationService,
      mockCommunityBuildingValidationService,
      mockGiftAidValidationService
    )
  }

  "UpscanCallbackService.processCallback" should {

    "return Success when handling successful callback (" + fileStatusReady + ")" in {

      val (service, mockRepository, _, _, _, _, _) = createService()

      val existingStatus = VerifyingStatus(
        claimId = claimId,
        reference = reference,
        validationType = ValidationType.GiftAid,
        createdAt = timestamp,
        updatedAt = timestamp
      )

      when(mockRepository.findByReference(eqTo(claimId), eqTo(reference)))
        .thenReturn(Future.successful(Some(existingStatus)))

      when(mockRepository.update(eqTo(claimId), eqTo(reference), any[ClaimValidationStatus]))
        .thenReturn(Future.successful(true))

      val callbackRequest = UpscanSuccessRequest(
        reference = reference,
        downloadUrl = NonEmptyString.unsafeFrom("https://bucketName.s3.eu-west-2.amazonaws.com?1235676"),
        fileStatus = NonEmptyString.unsafeFrom(fileStatusReady),
        uploadDetails = UploadDetails(
          fileName = NonEmptyString.unsafeFrom("test.ods"),
          fileMimeType = NonEmptyString.unsafeFrom("application/vnd.oasis.opendocument.spreadsheet"),
          uploadTimestamp = timestamp,
          checksum = NonEmptyString.unsafeFrom("396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100"),
          size = 987
        )
      )

      val result = service.processCallback(claimId, callbackRequest).futureValue

      result shouldBe (CallbackResult.StartValidation, Some(existingStatus))
      verify(mockRepository).findByReference(eqTo(claimId), eqTo(reference))
      verify(mockRepository).update(eqTo(claimId), eqTo(reference), any[ValidatingStatus])
    }

    "return Success when handling failure callback (QUARANTINE)" in {

      val (service, mockRepository, _, _, _, _, _) = createService()

      val existingStatus = VerifyingStatus(
        claimId = claimId,
        reference = reference,
        validationType = ValidationType.GiftAid,
        createdAt = timestamp,
        updatedAt = timestamp
      )

      when(mockRepository.findByReference(eqTo(claimId), eqTo(reference)))
        .thenReturn(Future.successful(Some(existingStatus)))

      when(mockRepository.update(eqTo(claimId), eqTo(reference), any[ClaimValidationStatus]))
        .thenReturn(Future.successful(true))

      val callbackRequest = UpscanFailureRequest(
        reference = reference,
        fileStatus = NonEmptyString.unsafeFrom(fileStatusFailed),
        failureDetails = FailureDetails(
          failureReason = FailureReason.Quarantine,
          message = "This file has a virus"
        )
      )

      val result = service.processCallback(claimId, callbackRequest).futureValue

      result shouldBe (CallbackResult.Success, Some(existingStatus))
      verify(mockRepository).findByReference(eqTo(claimId), eqTo(reference))
      verify(mockRepository).update(eqTo(claimId), eqTo(reference), any[VerificationFailedStatus])
    }

    "return Success when handling failure callback (REJECTED)" in {

      val (service, mockRepository, _, _, _, _, _) = createService()

      val existingStatus = VerifyingStatus(
        claimId = claimId,
        reference = reference,
        validationType = ValidationType.GiftAid,
        createdAt = timestamp,
        updatedAt = timestamp
      )

      when(mockRepository.findByReference(eqTo(claimId), eqTo(reference)))
        .thenReturn(Future.successful(Some(existingStatus)))

      when(mockRepository.update(eqTo(claimId), eqTo(reference), any[ClaimValidationStatus]))
        .thenReturn(Future.successful(true))

      val callbackRequest = UpscanFailureRequest(
        reference = reference,
        fileStatus = NonEmptyString.unsafeFrom(fileStatusFailed),
        failureDetails = FailureDetails(
          failureReason = FailureReason.Rejected,
          message = "MIME type not allowed"
        )
      )

      val result = service.processCallback(claimId, callbackRequest).futureValue

      result shouldBe (CallbackResult.Success, Some(existingStatus))
      verify(mockRepository).findByReference(eqTo(claimId), eqTo(reference))
      verify(mockRepository).update(eqTo(claimId), eqTo(reference), any[VerificationFailedStatus])
    }

    "return Success when handling failure callback (UNKNOWN)" in {

      val (service, mockRepository, _, _, _, _, _) = createService()

      val existingStatus = VerifyingStatus(
        claimId = claimId,
        reference = reference,
        validationType = ValidationType.GiftAid,
        createdAt = timestamp,
        updatedAt = timestamp
      )

      when(mockRepository.findByReference(eqTo(claimId), eqTo(reference)))
        .thenReturn(Future.successful(Some(existingStatus)))

      when(mockRepository.update(eqTo(claimId), eqTo(reference), any[ClaimValidationStatus]))
        .thenReturn(Future.successful(true))

      val callbackRequest = UpscanFailureRequest(
        reference = reference,
        fileStatus = NonEmptyString.unsafeFrom(fileStatusFailed),
        failureDetails = FailureDetails(
          failureReason = FailureReason.Unknown,
          message = "Something unknown happened"
        )
      )

      val result = service.processCallback(claimId, callbackRequest).futureValue

      result shouldBe (CallbackResult.Success, Some(existingStatus))
      verify(mockRepository).findByReference(eqTo(claimId), eqTo(reference))
      verify(mockRepository).update(eqTo(claimId), eqTo(reference), any[VerificationFailedStatus])
    }

    "return NotFound when claim/reference not found" in {

      val (service, mockRepository, _, _, _, _, _) = createService()

      when(mockRepository.findByReference(any[String], any[String]))
        .thenReturn(Future.successful(Option.empty[ClaimValidationStatus]))

      val callbackRequest = UpscanSuccessRequest(
        reference = reference,
        downloadUrl = NonEmptyString.unsafeFrom(downloadUrl),
        fileStatus = NonEmptyString.unsafeFrom(fileStatusReady),
        uploadDetails = UploadDetails(
          fileName = NonEmptyString.unsafeFrom(fileName),
          fileMimeType = NonEmptyString.unsafeFrom(applicationPdfMimeType),
          uploadTimestamp = timestamp,
          checksum = NonEmptyString.unsafeFrom(checksum),
          size = 100
        )
      )

      val result = service.processCallback(claimId, callbackRequest).futureValue

      result shouldBe (CallbackResult.NotFound, None)
      verify(mockRepository).findByReference(eqTo(claimId), eqTo(reference))
      verify(mockRepository, never()).update(any[String], any[String], any[ClaimValidationStatus])
    }

    "return UpdateFailed when document disappears between find and update" in {

      val (service, mockRepository, _, _, _, _, _) = createService()

      val existingStatus = VerifyingStatus(
        claimId = claimId,
        reference = reference,
        validationType = ValidationType.GiftAid,
        createdAt = timestamp,
        updatedAt = timestamp
      )

      when(mockRepository.findByReference(eqTo(claimId), eqTo(reference)))
        .thenReturn(Future.successful(Some(existingStatus)))

      when(mockRepository.update(eqTo(claimId), eqTo(reference), any[ClaimValidationStatus]))
        .thenReturn(Future.successful(false))

      val callbackRequest = UpscanSuccessRequest(
        reference = reference,
        downloadUrl = NonEmptyString.unsafeFrom(downloadUrl),
        fileStatus = NonEmptyString.unsafeFrom(fileStatusReady),
        uploadDetails = UploadDetails(
          fileName = NonEmptyString.unsafeFrom(fileName),
          fileMimeType = NonEmptyString.unsafeFrom(applicationPdfMimeType),
          uploadTimestamp = timestamp,
          checksum = NonEmptyString.unsafeFrom(checksum),
          size = 100
        )
      )

      val result = service.processCallback(claimId, callbackRequest).futureValue

      result shouldBe (CallbackResult.UpdateFailed, Some(existingStatus))
      verify(mockRepository).findByReference(eqTo(claimId), eqTo(reference))
      verify(mockRepository).update(eqTo(claimId), eqTo(reference), any[ValidatingStatus])
    }

    "return CallbackResult.StartValidation when fileStatus is not AWAITING_UPLOAD or VERIFYING" in {
      val (
        service,
        mockRepository,
        _,
        _,
        _,
        _,
        _
      ) = createService()

      val existingStatus = VerifyingStatus(
        claimId = claimId,
        reference = reference,
        validationType = ValidationType.GiftAid,
        createdAt = timestamp,
        updatedAt = timestamp
      )

      val request = UpscanSuccessRequest(
        reference = reference,
        downloadUrl = downloadUrl,
        fileStatus = fileStatusReady,
        uploadDetails = UploadDetails(
          fileName,
          odsMimeType,
          timestamp,
          checksum,
          100
        )
      )

      when(mockRepository.findByReference(eqTo(claimId), eqTo(reference)))
        .thenReturn(Future.successful(Some(existingStatus)))

      when(mockRepository.update(any(), any(), any()))
        .thenReturn(Future.successful(true))

      val result = service.processCallback(claimId, request).futureValue

      result shouldBe (CallbackResult.StartValidation, Some(existingStatus))

      verify(mockRepository).findByReference(eqTo(claimId), eqTo(reference))
    }

    "throw exception when repository throws exception" in {

      val (service, mockRepository, _, _, _, _, _) = createService()

      val exception = new RuntimeException("Database error")

      when(mockRepository.findByReference(eqTo(claimId), eqTo(reference)))
        .thenReturn(Future.failed(exception))

      val callbackRequest = UpscanSuccessRequest(
        reference = reference,
        downloadUrl = NonEmptyString.unsafeFrom(downloadUrl),
        fileStatus = NonEmptyString.unsafeFrom(fileStatusReady),
        uploadDetails = UploadDetails(
          fileName = NonEmptyString.unsafeFrom(fileName),
          fileMimeType = NonEmptyString.unsafeFrom(applicationPdfMimeType),
          uploadTimestamp = timestamp,
          checksum = NonEmptyString.unsafeFrom(checksum),
          size = 100
        )
      )

      val resultFuture = service.processCallback(claimId, callbackRequest)

      whenReady(resultFuture.failed) {
        case ex: RuntimeException =>
          ex.getMessage shouldBe "Database error"
        case other =>
          fail(s"Unexpected exception: $other")
      }
    }
  }

  "UpscanCallbackService.processValidation" should {

    "return Unit when common file validation fails" in {

      val (
        service,
        mockRepository,
        mockCommonFileValidation,
        _,
        _,
        _,
        _
      ) = createService()

      val status = ValidatingStatus(
        claimId,
        reference,
        ValidationType.OtherIncome,
        timestamp,
        timestamp
      )

      val request = UpscanSuccessRequest(
        reference,
        downloadUrl,
        fileStatusReady,
        uploadDetails = UploadDetails(
          fileName = fileName,
          fileMimeType = applicationPdfMimeType,
          uploadTimestamp = timestamp,
          checksum = checksum,
          size = 100
        )
      )

      val error = ValidationError(
        field = "file",
        error = "Invalid file type"
      )

      when(mockCommonFileValidation.validateFile(any(), eqTo(ValidationType.OtherIncome)))
        .thenReturn(Future.successful(Left(error)))

      when(mockRepository.update(any(), any(), any()))
        .thenReturn(Future.successful(true))

      service.processValidation(claimId, request, Some(status)).futureValue

      verify(mockCommonFileValidation).validateFile(any(), eqTo(ValidationType.OtherIncome))
    }

    "validate OtherIncome and update status" in {
      val (
        service,
        mockRepository,
        mockCommonFileValidation,
        mockOtherIncomeValidationService,
        _,
        _,
        _
      ) = createService()

      val status = ValidatingStatus(
        claimId,
        reference,
        ValidationType.OtherIncome,
        timestamp,
        timestamp
      )

      val request = UpscanSuccessRequest(
        reference,
        downloadUrl,
        fileStatusReady,
        uploadDetails = UploadDetails(
          fileName = fileName,
          fileMimeType = applicationPdfMimeType,
          uploadTimestamp = timestamp,
          checksum = checksum,
          size = 100
        )
      )

      when(mockCommonFileValidation.validateFile(any(), eqTo(ValidationType.OtherIncome)))
        .thenReturn(Future.successful(Right(())))

      when(mockOtherIncomeValidationService.validate(any[String]))
        .thenReturn(Future.successful((Nil, Some(OtherIncomeData(Some(BigDecimal(1234.56)), None, None, Nil)))))

      when(mockRepository.update(any(), any(), any()))
        .thenReturn(Future.successful(true))

      service.processValidation(claimId, request, Some(status)).futureValue

      verify(mockOtherIncomeValidationService).validate(any[String])
      verify(mockRepository).update(any(), any(), any())
    }

    "validate CommunityBuildings and update status" in {
      val (
        service,
        mockRepository,
        mockCommonFileValidation,
        _,
        _,
        mockCommunityBuildingValidationService,
        _
      ) = createService()

      val status = ValidatingStatus(
        claimId,
        reference,
        ValidationType.CommunityBuildings,
        timestamp,
        timestamp
      )

      val request = UpscanSuccessRequest(
        reference,
        downloadUrl,
        fileStatusReady,
        uploadDetails = UploadDetails(
          fileName = fileName,
          fileMimeType = applicationPdfMimeType,
          uploadTimestamp = timestamp,
          checksum = checksum,
          size = 100
        )
      )

      when(mockCommonFileValidation.validateFile(any(), eqTo(ValidationType.CommunityBuildings)))
        .thenReturn(Future.successful(Right(())))

      when(mockCommunityBuildingValidationService.validate(any[String]))
        .thenReturn(Future.successful((Nil, Some(CommunityBuildingData(None, Nil)))))

      when(mockRepository.update(any(), any(), any()))
        .thenReturn(Future.successful(true))

      service.processValidation(claimId, request, Some(status)).futureValue

      verify(mockCommunityBuildingValidationService).validate(any[String])
    }

    "validate ConnectedCharities and update status" in {
      val (
        service,
        mockRepository,
        mockCommonFileValidation,
        _,
        mockConnectedCharitiesValidationService,
        _,
        _
      ) = createService()

      val status = ValidatingStatus(
        claimId,
        reference,
        ValidationType.ConnectedCharities,
        timestamp,
        timestamp
      )

      val request = UpscanSuccessRequest(
        reference,
        downloadUrl,
        fileStatusReady,
        uploadDetails = UploadDetails(
          fileName = fileName,
          fileMimeType = applicationPdfMimeType,
          uploadTimestamp = timestamp,
          checksum = checksum,
          size = 100
        )
      )

      when(mockCommonFileValidation.validateFile(any(), eqTo(ValidationType.ConnectedCharities)))
        .thenReturn(Future.successful(Right(())))

      when(mockConnectedCharitiesValidationService.validate(any[String]))
        .thenReturn(Future.successful((Nil, Some(ConnectedCharitiesData(Nil)))))

      when(mockRepository.update(any(), any(), any()))
        .thenReturn(Future.successful(true))

      service.processValidation(claimId, request, Some(status)).futureValue

      verify(mockConnectedCharitiesValidationService).validate(any[String])
    }

    "validate GiftAid and update status" in {
      val (
        service,
        mockRepository,
        mockCommonFileValidation,
        _,
        _,
        _,
        mockGiftAidValidationService
      ) = createService()

      val status = ValidatingStatus(
        claimId,
        reference,
        ValidationType.GiftAid,
        timestamp,
        timestamp
      )

      val request = UpscanSuccessRequest(
        reference,
        downloadUrl,
        fileStatusReady,
        uploadDetails = UploadDetails(
          fileName = fileName,
          fileMimeType = applicationPdfMimeType,
          uploadTimestamp = timestamp,
          checksum = checksum,
          size = 100
        )
      )

      when(mockCommonFileValidation.validateFile(any(), eqTo(ValidationType.GiftAid)))
        .thenReturn(Future.successful(Right(())))

      when(mockGiftAidValidationService.validate(any[String]))
        .thenReturn(Future.successful((Nil, Some(GiftAidScheduleData(Some(LocalDate.of(2017, 11, 10)), None, Some(1450.00), Nil)))))

      when(mockRepository.update(any(), any(), any()))
        .thenReturn(Future.successful(true))

      service.processValidation(claimId, request, Some(status)).futureValue

      verify(mockGiftAidValidationService).validate(any[String])
    }

    "create ValidationFailedStatus when errors and data are present" in {
      val (
        service,
        mockRepository,
        mockCommonFileValidation,
        mockOtherIncomeValidationService,
        _,
        _,
        _
      ) = createService()

      val status = ValidatingStatus(
        claimId,
        reference,
        ValidationType.OtherIncome,
        timestamp,
        timestamp
      )

      val request = UpscanSuccessRequest(
        reference,
        downloadUrl,
        fileStatusReady,
        uploadDetails = UploadDetails(
          fileName,
          applicationPdfMimeType,
          timestamp,
          checksum,
          100
        )
      )

      val errors = List(ValidationError("field", "invalid"))

      when(mockCommonFileValidation.validateFile(any(), eqTo(ValidationType.OtherIncome)))
        .thenReturn(Future.successful(Right(())))

      when(mockOtherIncomeValidationService.validate(any[String]))
        .thenReturn(
          Future.successful((errors, Some(OtherIncomeData(None, None, None, Nil))))
        )

      when(mockRepository.update(any(), any(), any()))
        .thenReturn(Future.successful(true))

      service.processValidation(claimId, request, Some(status)).futureValue

      val statusCaptor = ArgumentCaptor.forClass(classOf[ClaimValidationStatus])

      verify(mockRepository).update(
        eqTo(claimId),
        eqTo(reference),
        statusCaptor.capture()
      )

      val updatedStatus = statusCaptor.getValue
      updatedStatus shouldBe a[ValidationFailedStatus]

      val failedStatus = updatedStatus.asInstanceOf[ValidationFailedStatus]
      failedStatus.errors shouldBe errors
      failedStatus.otherIncomeData shouldBe defined
    }

    "create ValidationFailedStatus when errors are present but data is None" in {
      val (
        service,
        mockRepository,
        mockCommonFileValidation,
        mockOtherIncomeValidationService,
        _,
        _,
        _
      ) = createService()

      val status = ValidatingStatus(
        claimId,
        reference,
        ValidationType.OtherIncome,
        timestamp,
        timestamp
      )

      val request = UpscanSuccessRequest(
        reference,
        downloadUrl,
        fileStatusReady,
        uploadDetails = UploadDetails(
          fileName,
          applicationPdfMimeType,
          timestamp,
          checksum,
          100
        )
      )

      val errors = List(ValidationError("field", "invalid"))

      when(mockCommonFileValidation.validateFile(any(), eqTo(ValidationType.OtherIncome)))
        .thenReturn(Future.successful(Right(())))

      when(mockOtherIncomeValidationService.validate(any[String]))
        .thenReturn(Future.successful((errors, None)))

      when(mockRepository.update(any(), any(), any()))
        .thenReturn(Future.successful(true))

      service.processValidation(claimId, request, Some(status)).futureValue

      val statusCaptor = ArgumentCaptor.forClass(classOf[ClaimValidationStatus])
      verify(mockRepository).update(eqTo(claimId), eqTo(reference), statusCaptor.capture())

      val updatedStatus = statusCaptor.getValue
      updatedStatus shouldBe a[ValidationFailedStatus]
      updatedStatus.asInstanceOf[ValidationFailedStatus].errors shouldBe errors
    }

    "create ValidationFailedStatus with empty errors when no data and no errors" in {
      val (
        service,
        mockRepository,
        mockCommonFileValidation,
        mockOtherIncomeValidationService,
        _,
        _,
        _
      ) = createService()

      val status = ValidatingStatus(
        claimId,
        reference,
        ValidationType.OtherIncome,
        timestamp,
        timestamp
      )

      val request = UpscanSuccessRequest(
        reference,
        downloadUrl,
        fileStatusReady,
        uploadDetails = UploadDetails(
          fileName,
          applicationPdfMimeType,
          timestamp,
          checksum,
          100
        )
      )

      when(mockCommonFileValidation.validateFile(any(), eqTo(ValidationType.OtherIncome)))
        .thenReturn(Future.successful(Right(())))

      when(mockOtherIncomeValidationService.validate(any[String]))
        .thenReturn(Future.successful((Nil, None))) // no errors, no data

      when(mockRepository.update(any(), any(), any()))
        .thenReturn(Future.successful(true))

      service.processValidation(claimId, request, Some(status)).futureValue

      val statusCaptor = ArgumentCaptor.forClass(classOf[ClaimValidationStatus])
      verify(mockRepository).update(eqTo(claimId), eqTo(reference), statusCaptor.capture())

      val updatedStatus = statusCaptor.getValue
      updatedStatus shouldBe a[ValidationFailedStatus]
      updatedStatus.asInstanceOf[ValidationFailedStatus].errors shouldBe empty
    }

    "return unit when success request but no existing status" in {
      val (service, _, _, _, _, _, _) = createService()

      val request = UpscanSuccessRequest(
        reference,
        downloadUrl,
        fileStatusReady,
        uploadDetails = UploadDetails(
          fileName = fileName,
          fileMimeType = applicationPdfMimeType,
          uploadTimestamp = timestamp,
          checksum = checksum,
          size = 100
        )
      )

      service.processValidation(claimId, request, None).futureValue
    }

    "return unit for failure callback request" in {
      val (service, _, _, _, _, _, _) = createService()

      val request = UpscanFailureRequest(
        reference,
        fileStatusFailed,
        FailureDetails(FailureReason.Unknown, "error")
      )

      service.processValidation(claimId, request, None).futureValue
    }

    "fallback ValidationFailedStatus when attachData is None and errors + data exist" in {
      val (
        service,
        mockRepository,
        mockCommonFileValidation,
        _,
        _,
        _,
        _
      ) = createService()

      val status = ValidatingStatus(
        claimId,
        reference,
        ValidationType.OtherIncome,
        timestamp,
        timestamp
      )

      val request = UpscanSuccessRequest(
        reference,
        downloadUrl,
        fileStatusReady,
        UploadDetails(fileName, applicationPdfMimeType, timestamp, checksum, 100)
      )

      val error = ValidationError("file", "invalid")

      when(mockCommonFileValidation.validateFile(any(), eqTo(ValidationType.OtherIncome)))
        .thenReturn(Future.successful(Left(error)))

      when(mockRepository.update(any(), any(), any()))
        .thenReturn(Future.successful(true))

      service.processValidation(claimId, request, Some(status)).futureValue

      val captor = ArgumentCaptor.forClass(classOf[ClaimValidationStatus])
      verify(mockRepository).update(eqTo(claimId), eqTo(reference), captor.capture())

      captor.getValue shouldBe a[ValidationFailedStatus]
    }

    "return Success and None when status is already processed" in {
      val (service, mockRepository, _, _, _, _, _) = createService()

      val existingStatus = ValidatedStatus(
        claimId = claimId,
        reference = reference,
        validationType = ValidationType.GiftAid,
        createdAt = timestamp,
        updatedAt = timestamp
      )

      val request = UpscanSuccessRequest(
        reference,
        downloadUrl,
        fileStatusReady,
        UploadDetails(fileName, odsMimeType, timestamp, checksum, 100)
      )

      when(mockRepository.findByReference(eqTo(claimId), eqTo(reference)))
        .thenReturn(Future.successful(Some(existingStatus)))

      val result = service.processCallback(claimId, request).futureValue

      result shouldBe (CallbackResult.Success, None)
      verify(mockRepository, never()).update(any(), any(), any())
    }

    "return CallbackResult.Success and status as VerificationFailedStatus when fileMimeType is invalid" in {
      val (
        service,
        mockRepository,
        _,
        _,
        _,
        _,
        _
      ) = createService()

      val existingStatus = VerificationFailedStatus(
        claimId = claimId,
        reference = reference,
        validationType = ValidationType.GiftAid,
        failureDetails = FailureDetails(
          failureReason = FailureReason.Rejected,
          message = s"MIME type application/pdf is not allowed"
        ),
        createdAt = timestamp,
        updatedAt = timestamp
      )

      val request = UpscanSuccessRequest(
        reference = reference,
        downloadUrl = downloadUrl,
        fileStatus = fileStatusReady,
        uploadDetails = UploadDetails(
          fileName,
          applicationPdfMimeType,
          timestamp,
          checksum,
          100
        )
      )

      when(mockRepository.findByReference(eqTo(claimId), eqTo(reference)))
        .thenReturn(Future.successful(Some(existingStatus)))

      when(mockRepository.update(any(), any(), any()))
        .thenReturn(Future.successful(true))

      val result = service.processCallback(claimId, request).futureValue

      result shouldBe (CallbackResult.Success, Some(existingStatus))

      verify(mockRepository).findByReference(eqTo(claimId), eqTo(reference))
    }

  }
}
