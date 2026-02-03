def call(String bucketName) {
    echo "Verify the existence of the bucket: ${bucketName}"

    def status = sh(
        script: "aws s3api head-bucket --bucket ${bucketName} --region eu-west-1",
        returnStatus: true
    )

    if (status == 0) {
        echo "OK, bucket exists"
    } else {
        echo "Bucket doesn't exist"
    }
}

