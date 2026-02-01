def call() {
     sh '''
        echo "Listing all buckets..."
    BUCKETS=$(aws s3api list-buckets --query "Buckets[].Name" --output text)

    for BUCKET in $BUCKETS; do
        echo "Deleting contents of bucket: $BUCKET"
        # Supprimer tout le contenu
        aws s3 rm "s3://$BUCKET" --recursive

        echo "Deleting bucket: $BUCKET"
        # Supprimer le bucket
        aws s3api delete-bucket --bucket "$BUCKET"
    done

    echo "All buckets have been deleted."
    '''
}
