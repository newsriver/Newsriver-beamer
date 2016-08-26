node {


  if(env.BRANCH_NAME==null){
    env.BRANCH_NAME = "master"
  }

  stage 'checkout lib'
  checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'Newsriver-lib']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'newsriver-lib', url: 'git@github.com:newsriver/Newsriver-lib.git']]])
  stage 'checkout project'
  checkout scm
  stage 'set-up project'
  writeFile file: 'settings.gradle', text: '''rootProject.name = \'Newsriver-beamer\' \ninclude \'Newsriver-lib\''''

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
      url: 'http://46.4.71.105:8080/',
      forceUpdate: true,
      appid: '/newsriver/newsriver-beamer',
      docker: 'docker-registry.newsriver.io:5000/newsriver-beamer:'+env.BUILD_TAG
      )
}

def deployDockerImage(){

  stage 'build'
  initDocker()

  sh 'gradle clean'
  sh 'gradle fatJar'

  dir('docker'){
    deleteDir()
  }

  sh 'mkdir docker'

  dir('docker'){
    sh 'cp ../build/libs/Newsriver-beamer-*.jar .'
    sh 'cp ../Dockerfile .'
    docker.withRegistry('https://docker-registry.newsriver.io:5000/') {
        stage 'build docker image'
        def image = docker.build('newsriver-beamer:'+env.BUILD_TAG)
        stage 'upload docker image'
        image.push('latest')
    }
  }

}


def initDocker(){
  def status = sh(script: 'docker ps', returnStatus: true)
  if(status!=0){
    sh 'service docker start'
  }
}
