def call() {
    sh '''
       echo "hello this is buckets scaning"
        BUCKETS_NAME=$(aws s3api list-buckets --query "Buckets[].Name" --output text)

        for BUCKET_NAME in $BUCKETS_NAME; do
        echo "this is bucket copy"
            aws s3 cp \
              "s3://$BUCKET_NAME/" \
              "s3://${BUCKET_NAME}-new" \
              --recursive \
              --quiet
        done
    '''
}
