# hmpps-prisoner-location-api
[![repo standards badge](https://img.shields.io/badge/endpoint.svg?&style=flat&logo=github&url=https%3A%2F%2Foperations-engineering-reports.cloud-platform.service.justice.gov.uk%2Fapi%2Fv1%2Fcompliant_public_repositories%2Fhmpps-prisoner-location-api)](https://operations-engineering-reports.cloud-platform.service.justice.gov.uk/public-report/hmpps-prisoner-location-api "Link to report")
[![Docker Repository on ghcr](https://img.shields.io/badge/ghcr.io-repository-2496ED.svg?logo=docker)](https://ghcr.io/ministryofjustice/hmpps-prisoner-location-api)
[![API docs](https://img.shields.io/badge/API_docs_-view-85EA2D.svg?logo=swagger)](https://prisoner-location-api-dev.prison.service.justice.gov.uk/swagger-ui/index.html)

This project contains endpoints to download the prisoner location data, also known as Offloc.
The full list of endpoints can be found in the API docs, see the view link above.

## Accessing S3 locally

```shell
kubectl run -it --rm debug --image=ghcr.io/ministryofjustice/hmpps-devops-tools:latest --restart=Never --overrides='{ "spec": { "serviceAccount": "hmpps-prisoner-location-api" }  }' -- bash
```

will start a shell connecting to AWS using the service account credentials.  You should then find that
`AWS_WEB_IDENTITY_TOKEN_FILE` and `AWS_ROLE_ARN` are automatically set.

In order to run commands the `bucket_name` is required, which is contained in the
`hmpps-prisoner-location-bucket` secret.

### Listing objects
```shell
aws s3 ls s3://<bucket_name>
```
will show all the objects in the bucket.

### Creating objects
`/tmp` in the pod will have write access, so you can create files in there for upload to s3.
```shell
aws s3 cp /tmp/20240116.zip s3://<bucket_name>
```
will then copy the `20240116.zip` file to s3.

### Tidying up
```shell
aws s3 rm s3://<bucket_name>/filename.zip
```
will then delete `filename.zip` from s3.
