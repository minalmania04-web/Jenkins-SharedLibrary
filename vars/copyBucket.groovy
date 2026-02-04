
def call(String sourceBucket, String targetBucket) {
    def config = [
        source: sourceBucket,
        target: targetBucket,
        envName: enviroment // Ajouté pour correspondre à l'utilisation dans validateInput
    ]
stage('Validate then Inputs')
    {
    validateInput(config)
    }
stage('Detecting the environement')
    {
    EmailApprouvment(enviroment)
    }
stage('checking the existance of the buckets')
    { checkBucketExistence(config.source)
    }
    checkBucketExistence(config.target)
    s3copy(config.source, config.target)
}

def s3copy(String sourceBucket, String targetBucket) {
    echo "***** This step will do a copy of the content from the **** : ${sourceBucket}"
    
    def confirmation = input(
        id: 'confirmCopy',
        message: "ATTENTION, Are you sure you want to copy the content of s3://${sourceBucket} to s3://${targetBucket}?",
        parameters: [
            string(name: 'CONFIRM', description: 'Enter YES to continue', defaultValue: '')
        ]
    )

    if (confirmation != "YES") {
        error "Action is aborted, the confirmation 'YES' was not recieved."
    }

    sh """
        set -e
        echo "Content of Source bucket : (${sourceBucket}) :"
        aws s3 ls s3://${sourceBucket} --recursive --human-readable --summarize
        
        echo "Content of target bucket (${targetBucket}) :"
        aws s3 ls s3://${targetBucket} --recursive --human-readable --summarize
        
        echo "launch the copy process"
        aws s3 cp s3://${sourceBucket} s3://${targetBucket} --recursive
        
        echo "Copy process is done, please check the new content of your bucket down below"
        echo "New Content of target bucket : (${targetBucket}) :"
        aws s3 ls s3://${targetBucket} --recursive --human-readable --summarize
    """
}
def detectingenviroment()
{
    if(binding.hasVariable('params') && params?.enviroment)
    { return params.enviroment
    }
}
def EmailApprouvment() {
    String Actualenviroment = detectingenviroment()
    if (Actualenviroment == "prod" || Actualenviroment == "learn" || Actualenviroment == "val") {
        sendEmailApproval(env.BUILD_TAG)
        timeout(time: 1, unit: 'DAYS') {
            input message: 'The build need an approval to continue. Do you want to approve this?',
                  submitter: "${env.APPROVAL_BUILD_USERS}"
        }
        echo 'Deployment Approved and Continue!'
        sendEmailApproval(env.BUILD_TAG, true)
    } else {
        echo 'this is dev enviroment'
    }
}

def validateInput(Map config) {
    if (!config.source) {
        error "this input is empty, Source Bucket is required"
    }
    if (!config.target) {
        error "this input is empty, target Bucket is required"
    }
}

def checkBucketExistence(String bucketName) {
    echo "Verify the existence of : ${bucketName}"
    
    def status = sh(
        script: "aws s3api head-bucket --bucket ${bucketName}",
        returnStatus: true
    )

    if (status == 0) {
        echo "OK : Bucket '${bucketName}' existe."
    } else {
        error "ERREUR : Bucket '${bucketName}' doesn't existe or not accessible."
    }
}
