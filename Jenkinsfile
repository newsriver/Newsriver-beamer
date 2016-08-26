#!groovy​
node {

  def project-name = 'Newsriver-beamer'
  def marathon-app-id = '/newsriver/newsriver-beamer'
  def docker-registry = 'docker-registry.newsriver.io:5000'
  def marathon-url = 'http://46.4.71.105:8080/'

  stage 'checkout lib'
  checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'Newsriver-lib']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'newsriver-lib', url: 'git@github.com:newsriver/Newsriver-lib.git']]])
  stage 'checkout project'
  checkout scm
  stage 'set-up project'
  writeFile file: 'settings.gradle', text: '''rootProject.name = \''''+project-name+'''\' \ninclude \'Newsriver-lib\''''

  stage 'compile'
  sh 'gradle compileJava'

  stage 'test'
  sh 'gradle test'

  if(env.BRANCH_NAME=="master"){
    deployDockerImage()
    restartDockerContainer()
  }
}


def restartDockerContainer(){
  stage 'deploy application'
  marathon(
      url: marathon-url,
      forceUpdate: true,
      appid: marathon-app-id,
      docker: docker-registry + '/'+project-name+':'+env.BUILD_NUMBER
      )
}

def deployDockerImage(){

  stage 'build'
  initDocker()

  sh 'gradle fatJar'

  dir('docker'){
    deleteDir()
  }
  sh 'mkdir docker'

  dir('docker'){
    sh 'cp ../build/libs/'+project-name+'-*.jar .'
    sh 'cp ../Dockerfile .'
    docker.withRegistry('https://'+docker-registry+'/') {
        stage 'build docker image'
        def image = docker.build(project-name+":latest")
        stage 'upload docker image'
        image.push(env.BUILD_NUMBER)
    }
  }

}


def initDocker(){
  def status = sh(script: 'docker ps', returnStatus: true)
  if(status!=0){
    sh 'service docker start'
  }
}
