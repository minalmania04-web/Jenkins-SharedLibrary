
def call(Map params) {
    def config = [
        source : params.source ?: "",
        target : params.target ?: "",
        purge  : [
            enabled: params.purge?.enabled ?: false
        ]
    ]
stage('Validate then Inputs')
    {
    validateInput(config)
    }
stage('Detecting the environement')
    {
    emailApprouvmentenv()
    }
stage('checking the existance of the buckets')
    { checkBucketExistence(config)
    }
    checkBucketExistence(config)
    s3copy(config)
}

def s3copy(Map config) {
    String op = "s3 cp"
    String process = "copy"
    String recursive = "--recursive"
    echo "${config.purge.enabled}"
    if(config.purge.enabled)
    {
        op = "s3 sync"
        process = "sync"
        recursive =""
    }
    echo "***** This step will do a ${process} of the content from the **** : ${config.source}"
    
    def confirmation = input(
        id: "confirm-${process}",
        message: "ATTENTION, Are you sure you want to ${process} the content of s3://${config.source} to s3://${config.target}?",
        parameters: [
            string(name: 'CONFIRM', description: 'Enter YES to continue', defaultValue: '')
        ]
    )

    if (confirmation != "YES") {
        error "Action is aborted, the confirmation 'YES' was not recieved."
    }

    sh """
        set -e
        echo "Content of Source bucket : (${config.source}) :"
        aws s3 ls s3://${config.source} --recursive --human-readable --summarize
        
        echo "Content of target bucket (${config.target}) :"
        aws s3 ls s3://${config.target} --recursive --human-readable --summarize
        
        echo "launch the copy process"
        aws ${op} s3://${config.source} s3://${config.target} ${recursive}
        
        echo "Copy process is done, please check the new content of your bucket down below"
        echo "New Content of target bucket : (${config.target}) :"
        aws s3 ls s3://${config.source} --recursive --human-readable --summarize
    """
}
def detectingenviroment()
{
    if(params.enviroment)
    { return params.enviroment
    }
}
def emailApprouvmentenv(Map config) {
    def bucket_env= sh( 
        script : " aws s3api get-bucket-tagging --bucket ${target.bucket} --query 'TargSet[?Key=='environment'].Value' --output text"
        returnStdout: true  )
    echo " ${bucket_env}"
    if(bucket_env == 'prod' || bucket_env == 'learn')    
    {
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

def checkBucketExistence(Map config) {
    echo "Verify the existence of : ${config.target}"
    
    def status = sh(
        script: "aws s3api head-bucket --bucket ${config.target}",
        returnStatus: true
    )

    if (status == 0) {
        echo "OK : Bucket '${config.target}' existe."
    } else {
        error "ERREUR : Bucket '${config.target}' doesn't existe or not accessible."
    }
}
