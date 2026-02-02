def call(Map config = [:]) 
{
   if(config.envName != "dev")
     {
          error "this is not dev env"
     }
    if(!config.sourceBucket)
     {
          error "source bucket empty"
     }
     if(!config.targetBucket)
     {
          error "target bucket empty"
     }
def sourceBucket = config.sourceBucket
def targetBucket = config.targetBucket

     checkBucketExistence(sourceBucket)
     checkBucketExistence(targetBucket)
     
     echo "this step will copy the content of ${sourceBucket} to the target ${targetBucket}

     sh """
      set -e 
      aws s3 cp s3://${sourceBucket}  s3://${targetBucket} --recursive
     """
     
     
}
