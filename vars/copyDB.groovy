import groovy.json.JsonSlurper
def call(Map params = [:]) {
    def config = [
        source: params.source ?: "",
        target: params.target ?: "",
        mode_copy: params.mode_copy ?: "scan",
        filetarget: params.filetarget?: "",
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

    if (!config.target && !config.filetarget) {
        error "target DynamoDB value not provided"
    }

    if (config.target && config.filetarget == config.source) {
        error "source DB and Target DB cannot be equal"
    }
      checkDBExistence(config.source)
    if(config.target)
    { 
        checkDBExistence(config.target)
    }
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
            aws dynamodb list-tags-of-resource --resource-arn \$TABLE_ARN  --region ${dbregion} --query "Tags[?Key=='env'].Value" --output text 
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
        if(config.filetarget){
           sh """ aws dynamodb scan --table-name ${config.source} --output json > ${config.filetarget} """
        }else{
        def scanCmd = "aws dynamodb scan --table-name ${config.source} --output json --region ${dbregion}"
        read_write(scanCmd, config)
    }
    }
    if (config.mode_copy == "query") {
        if (!config.'expression-condition') {
            error "key-condition-expression is required for this operation"
        }
        def queryCmd = "aws dynamodb query --table-name ${config.source} --key-condition-expression \"${config.'expression-condition'}\" --output json --region ${dbregion}"
        read_write(queryCmd, config)
    }
}

import groovy.json.JsonSlurper
import groovy.json.JsonOutput

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
        def data = parseDynamoResponse(queryResultRaw)        
        lastKey = data.nextKey
        isFinished = data.done
        def count = data.itemCount
        def batch_size = 25

        if (count > 0) {
            // Utilisation de writeFile pour éviter les problèmes d'escape de caractères dans le shell
            writeFile file: 'raw_data.json', text: queryResultRaw
            
            for (int NB = 0; NB < count; NB += batch_size) {
                sh """
                    echo '${queryResultRaw}' | jq '.Items[${NB}:${NB + batch_size}]' > query_result.json
                    jq '.Items[${NB}:${NB + batch_size}]' raw_data.json > query_result.json
                    jq '{"${config.target}": [.[] | {PutRequest: {Item: .}} ]}' query_result.json > batch_write.json
                    aws dynamodb batch-write-item --request-items file://batch_write.json --region ${dbregion}
                """
            }
            
            sh "rm -f raw_data.json query_result.json batch_write.json"
        }
    }
}



/**
 * L'annotation @NonCPS permet d'utiliser des objets non-sérialisables 
 * (comme ceux de JsonSlurper) car Jenkins ne tentera pas de sauvegarder 
 * l'état au milieu de cette fonction.
 */
@NonCPS
def parseDynamoResponse(String rawJson) {
    def json = new JsonSlurper().parseText(rawJson)
    return [
        itemCount: json.Items ? json.Items.size() : 0,
        done: json.LastEvaluatedKey == null,
        nextKey: json.LastEvaluatedKey ? JsonOutput.toJson(json.LastEvaluatedKey) : ""
    ]
}
