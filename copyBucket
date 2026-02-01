def Transfer_bucket_Content() {
    sh '''
        BUCKETS_NAME=$(aws s3api list-buckets --query "Buckets[].Name" --output text)

        for BUCKET_NAME in $BUCKETS_NAME; do
            aws s3 cp \
              "s3://$BUCKET_NAME/" \
              "s3://$BUCKET_NAME_NEW/" \
              --recursive \
              --quiet
        done
    '''
}
