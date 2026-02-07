def call(Map params = [:]) {
    def config = [
        source: params.source ?: "",
        target: params.target ?: "",
        mode_copy: params.mode_copy ?: "scan",
        'expression-condition': params.'expression-condition' ?: ""
    ]

    executeStages(config)
}

def executeStages(Map config) {
    stage('Validate the Inputs if exist') {
        validateInput(config)
    }

    stage('Detecting the environment') {
        defineEnvironment(config)
    }

    stage('DB copy process') {
        dbcopy(config)
    }
}

def validateInput(Map config) {
    if (!config.source) {
        error "source DynamoDB value not provided"
    }

    if (!config.target) {
        error "target DynamoDB value not provided"
    }

    if (config.target == config.source) {
        error "source DB and Target DB cannot be equal"
    }

    echo "source DynamoDB and Target is provided"
    checkDBExistence(config.target)
    checkDBExistence(config.source)
}

def checkDBExistence(String tableName) {
    def dbregion = "eu-west-1"
    def status = sh(
        script: "aws dynamodb describe-table --table-name ${tableName} --region ${dbregion}",
        returnStatus: true
    )

    if (status == 0) {
        echo "OK : Table '${tableName}' existe."
    } else {
        error "ERREUR : Table '${tableName}' doesn't exist or not accessible."
    }
    return status == 0
}

def defineEnvironment(Map config) {
    def dbregion = "eu-west-1"
    def db_env = sh(
        script: """
            TABLE_ARN=\$(aws dynamodb describe-table --table-name ${config.source} --region ${dbregion} --query Table.TableArn --output text)
            aws dynamodb list-tags-of-resource --resource-arn \$TABLE_ARN --query "Tags[?Key=='env'].Value" --output text 
        """,
        returnStdout: true
    ).trim()

    if (db_env == 'prod' || db_env == 'learn') {
        sendEmailApproval(env.BUILD_TAG)
        timeout(time: 1, unit: 'DAYS') {
            input message: "The Copy process in this environment needs an approval to continue. Do you want to approve this operation?",
                  submitter: "${env.APPROVAL_BUILD_USERS}"
        }
        echo 'Deployment Approved and Continue!'
        sendEmailApproval(env.BUILD_TAG, true)
    } else {
        echo "you are copying to bucket in dev environment"
    }
}

def dbcopy(Map config) {
    // Appel de la gestion du mode de facturation avant la copie
    do_copy(config)
}

def do_copy(Map config) {
     def dbregion = "eu-west-1"
    if (config.mode_copy == "scan") {
        def scanCmd = "aws dynamodb scan --table-name ${config.source} --output json --region ${dbregion)"
        read_write(scanCmd, config)
    }

    if (config.mode_copy == "query") {
        if (!config.'expression-condition') {
            error "key-condition-expression is required for this operation"
        }
        def queryCmd = "aws dynamodb query --table-name ${config.source} --key-condition-expression \"${config.'expression-condition'}\" --output json --region ${dbregion)"
        read_write(queryCmd, config)
    }
}

def read_write(String dbCmd, Map config) {
   def dbregion = "eu-west-1"
    def lastKey = ""
    def isFinished = false

    while (!isFinished) {
        def queryCmd = dbCmd
        if (lastKey) {
            queryCmd += " --exclusive-start-key '${lastKey}'"
        }

        def queryResultRaw = sh(script: queryCmd, returnStdout: true).trim()
        def queryJson = readJSON text: queryResultRaw

        if (queryJson.LastEvaluatedKey) {
            lastKey = groovy.json.JsonOutput.toJson(queryJson.LastEvaluatedKey)
        } else {
            isFinished = true
        }

        def count = queryJson.Items.size()
        def batch_size = 25

        if (count > 0) {
            for (int NB = 0; NB < count; NB += batch_size) {
                sh """
                    echo '${queryResultRaw}' | jq '.Items[${NB}:${NB + batch_size}]' > query_result.json
                    jq '{"${config.target}": [.[] | {PutRequest: {Item: .}} ]}' query_result.json > batch_write.json
                    aws dynamodb batch-write-item --request-items file://batch_write.json --region ${dbregion)
                """
            }
        }
    }
}
