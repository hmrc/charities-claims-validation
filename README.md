# Charities Claims Validation

This repository is being made public. The public repository is available at https://github.com/hmrc/charities-claims-validation

## Service Overview & Logic

This backend microservice (located in the MDTP protected zone) manages the validation, transformation, and storage of charity claim spreadsheets.

This service will be used by `Charities-management-frontend`

### Core Functions

1. **Transformation:** Converts spreadsheet data into JSON format.
2. **Validation:** Validates data against specific business rules (Gift Aid, Other Income, etc.).
3. **Storage:** Tracks upload lifecycle and persists validation results.

### Upload Lifecycle Logic

The service tracks files through specific statuses (`fileStatus`):

1. **`AWAITING_UPLOAD`**: Tracking created, but file not yet uploaded by user.
2. **`VERIFYING`**: File uploaded; awaiting virus scan/verification from Upscan.
3. **`VALIDATING`**: Virus scan passed; business data validation in progress.
4. **`VALIDATED`**: Data is valid and stored.
5. **`VERIFICATION_FAILED`**: Virus scan failed or invalid file type.
6. **`VALIDATION_FAILED`**: Business data contains errors.

### You can refer to the [documentation]([https://confluence.tools.tax.service.gov.uk/display/RBD/4.+Charities](https://confluence.tools.tax.service.gov.uk/pages/viewpage.action?spaceKey=RBD&title=I2+-+Charities+Claims+Validation+Backend+Microservice))

---

## API Endpoints

### 1. Create Upload Tracking

Initializes tracking for a new file upload.

* **Method:** `POST`
* **Path:** `/<claimId>/create-upload-tracking`
* **Response:**
```json
{ "success": true }

```



### 2. Get Upload Summary

Retrieves a list of all uploads and their current status for a claim.

* **Method:** `GET`
* **Path:** `/<claimId>/upload-results`
* **Response:**
```json
{
  "uploads": [
    {
      "reference": "UUID-String",
      "validationType": "GiftAid | OtherIncome | ...",
      "fileStatus": "VALIDATED",
      "uploadUrl": "String (Only if AWAITING_UPLOAD)"
    }
  ]
}

```



### 3. Get Upload Result

Retrieves detailed results for a specific file. The response structure changes based on the `fileStatus`.

* **Method:** `GET`
* **Path:** `/<claimId>/upload-results/<reference>`

**Response Scenarios:**

* **Awaiting/Verifying:** Returns basic status info.
* **Verification Failed (Virus/Mime):**
```json
{
  "fileStatus": "VERIFICATION_FAILED",
  "failureDetails": { "failureReason": "QUARANTINE", "message": "..." }
}

```


* **Validated (Success):** Returns the transformed JSON data (specific to the `validationType`).
```json
{
  "fileStatus": "VALIDATED",
  "giftAidScheduleData": { ... },       // If type is GiftAid
  "otherIncomeScheduleData": { ... },   // If type is OtherIncome
  "communityBuildingsData": { ... },    // If type is CommunityBuildings
  "connectedCharitiesData": { ... }     // If type is ConnectedCharities
}

```


* **Validation Failed (Data Errors):**
```json
{
  "fileStatus": "VALIDATION_FAILED",
  "errors": { "field": "Cell Reference", "error": "Error description" }
}

```



### 4. Upscan Callback

Endpoint called by the Upscan service to notify the backend of file safety.

* **Method:** `POST`
* **Path:** `/<claimId>/upscan-callback`
* **Logic:** Accepts standard Upscan success/failure payloads. Triggers the internal transformation and validation logic.
* **Response:** `200 OK` (No body)

### 5. Update Upload Status

Manually updates the status of an upload (e.g., setting it to `VERIFYING` after the frontend completes the upload).

* **Method:** `PUT`
* **Path:** `/<claimId>/upload-results/<reference>`
* **Body:** `{ "fileStatus": "VERIFYING" }`
* **Response:**
```json
{ "success": true }

```



### 6. Delete single Upload

* **Method:** `DELETE`
* **Path:** `/<claimId>/upload-results/<reference>`
* **Response:**
```json
{ "success": true }

```

### 7. Delete all Uploads

* **Method:** `DELETE`
* **Path:** `/<claimId>/upload-results`
* **Response:**
```json
{ "success": true }

```

## Persistence
This service uses mongodb to persist user answers.

## Requirements
This service is written in Scala using the Play framework, so needs at least a JRE to run.

JRE/JDK 11 is recommended.

The service also depends on mongodb.

## Running the service
Using Service Manager, **sm2** uses the **DASS_CHARITIES_ALL** profile to start all services with the latest tagged releases.

```bash
sm2 --start DASS_CHARITIES_ALL
```
Run ```sm2 -s ``` to check what services are running

## Launching the service locally

Run the **sm2** command below to start all the services required for the Charities Claims Validation service.

```bash
sm2 -start DASS_CHARITIES_ALL
```
Run the **sm2** command below to stop Charities Claims Validation service.

```bash
sm2 -stop CHARITIES_CLAIMS_VALIDATION
```
Run the **sm2** command below to start Charities Claims Validation service locally.
> Note: this service runs on port 8032 by default

```bash
sbt run
```
***

## Testing

In order to run the tests locally you can run the following command:

```> sbt clean coverage test it/test coverageReport```

This will also generate a coverage report which should be over 90% for it to pass

### Scalafmt

To prevent formatting failures in a GitHub pull request, run the command ```sbt scalafmtAll``` before pushing to the remote repository.

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
