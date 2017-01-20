#!groovy​

def marathonAppId = '/newsriver/newsriver-beamer'
def projectName = 'newsriver-beamer'
def dockerRegistry = 'docker-registry-v2.newsriver.io:5000'
def marathonURL = 'http://leader.mesos:8080/'
node {

    stage 'checkout project'
    checkout scm
    stage 'checkout lib'
    checkout([$class: 'GitSCM', branches: [[name: '*/dc']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'Newsriver-lib']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'github', url: 'https://github.com/newsriver/Newsriver-lib.git']]])

    stage 'set-up project'
    writeFile file: 'settings.gradle', text: '''rootProject.name = \'''' + projectName + '''\' \ninclude \'Newsriver-lib\''''

    stage 'compile'
    sh 'gradle compileJava'

    stage 'test'
    sh 'gradle test'

    if (env.BRANCH_NAME == "master") {
        deployDockerImage(projectName, dockerRegistry)
        restartDockerContainer(marathonAppId, projectName, dockerRegistry, marathonURL)
    }

    if (env.BRANCH_NAME == "dc") {
        deployDockerImage(projectName, dockerRegistry)
        restartDockerContainer(marathonAppId, projectName, dockerRegistry, marathonURL)
    }

}


def restartDockerContainer(marathonAppId, projectName, dockerRegistry, marathonURL) {
    stage 'deploy application'
    marathon(
            url: "$marathonURL",
            forceUpdate: true,
            appid: "$marathonAppId",
            docker: "$dockerRegistry/$projectName:${env.BUILD_NUMBER}"
    )
}

def deployDockerImage(projectName, dockerRegistry) {

    stage 'build'
    initDocker()
    sh 'gradle clean'
    sh 'gradle shadowJar'

    dir('docker') {
        deleteDir()
    }
    sh 'mkdir docker'

    dir('docker') {
        sh "cp ../build/libs/$projectName-*.jar ."
        sh 'cp ../Dockerfile .'
        docker.withRegistry("https://$dockerRegistry/") {
            stage 'build docker image'
            def image = docker.build("$projectName:latest")
            stage 'upload docker image'
            image.push(env.BUILD_NUMBER)
        }
    }
}

def initDocker() {
    def status = sh(script: 'docker ps', returnStatus: true)
    if (status != 0) {
        sh 'service docker start'
    }
}