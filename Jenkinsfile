node('Docker CI slave') {
    def sbtHome = tool name: 'SBT 1.1.6', type: 'com.cloudbees.jenkins.plugins.customtools.CustomTool'

    try {
        stage('Push To ECR') {
            echo 'Starting to build docker image for Sysiphos...'

            env.PATH = "${sbtHome}:/home/ubuntu/.local/bin:${env.PATH}"

            checkout scm

            sh 'pip install awscli --upgrade --user'

            withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: '794925bb-f8ff-44e5-9e47-410ca2a962ae', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                sh 'aws --version'
                sh 'aws iam get-user'
                sh 'aws ecr get-login --no-include-email --region us-east-1 > ecrLogin'
                sh 'sudo sh ecrLogin'
                sh 'sudo SBT_OPTS="-Xms512M -Xmx2048M -Xss2M -XX:MaxMetaspaceSize=1024M" sbt ";clean;test;docker:publishLocal"'
                sh 'docker tag flowtick/sysiphos:latest 003250186609.dkr.ecr.us-east-1.amazonaws.com/sysiphos:latest'
                sh 'docker push  003250186609.dkr.ecr.us-east-1.amazonaws.com/sysiphos:latest'
            }
        }

        echo 'sending notifications'

        switch(currentBuild.result) {
            case 'UNSTABLE':
                slackSend channel: 'alerts-dataeng', color: '#FFFF00', message: "UNSTABLE: Job ${env.JOB_NAME} [${env.BUILD_NUMBER}] (${env.BUILD_URL})"
                break;
        }
    } catch (e) {
        manager.buildFailure()
        echo "ERROR: ${e.toString()}"
        slackSend channel: 'alerts-dataeng', color: '#FF0000', message: "FAILED: Job ${env.JOB_NAME} [${env.BUILD_NUMBER}] (${env.BUILD_URL})"
    }
}
