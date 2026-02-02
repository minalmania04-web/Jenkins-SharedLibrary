def call(String bucketName)
{
echo "verify the existance of the : ${bucketName}"
  def status = sh(
   script: "aws s3api head-bucket --bucket ${bucketName} --region "eu-west-1"
    returnStatus: true
  )
if(status == 0)
  {
    echo "ok , bucket existe"
  } else {
    echo "bucket doesn't exist"
  }
  
}
