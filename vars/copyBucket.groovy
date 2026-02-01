def call() {
     sh '''
        set -euo pipefail

        echo "Scanning all buckets..."
        BUCKETS_NAME=$(aws s3api list-buckets --query "Buckets[].Name" --output text)

        for BUCKET_NAME in $BUCKETS_NAME; do
            # ne traiter que les buckets qui se terminent par '-kitty'
            if [[ "$BUCKET_NAME" == *"-kitty" ]]; then
                DEST_BUCKET="${BUCKET_NAME}-new"
                echo "Copying content from $BUCKET_NAME to $DEST_BUCKET"
                aws s3 cp "s3://$BUCKET_NAME/" "s3://$DEST_BUCKET/" --recursive --quiet
            fi
        done
    '''
}
