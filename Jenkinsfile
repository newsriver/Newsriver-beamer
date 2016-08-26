node {


  if(env.BRANCH_NAME==null){
    env.BRANCH_NAME = "master"
  }

  stage 'Checkout Library'
  checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'Newsriver-lib']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'newsriver-lib', url: 'git@github.com:newsriver/Newsriver-lib.git']]])
  stage 'Checkout Beamer'
  checkout scm
  stage 'Write gradle project setting file'
  writeFile file: 'settings.gradle', text: '''include \'Newsriver-lib\''''

  stage 'compile'
  sh 'gradle compileJava'

  stage 'test'
  sh 'gradle test'

  if(env.BRANCH_NAME=="master"){
    deployDockerImage()
  }
}


def deployDockerImage(){

  stage 'Docker deploy'
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
        docker.build('newsriver-beamer:'+env.BUILD_TAG).push('latest')
    }
  }

}


def initDocker(){
  def status = sh(script: 'sudo service docker status', returnStatus: true)
  if(status!=0){
    sh 'sudo service docker start'
  }
}
