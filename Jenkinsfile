node {

  if(env.BRANCH_NAME==null){
    env.BRANCH_NAME = "master"
  }

  stage 'Checkout Library'
  checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'Newsriver-lib']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'newsriver-lib', url: 'git@github.com:newsriver/Newsriver-lib.git']]])
  stage 'Checkout Beamer'
  checkout([$class: 'GitSCM', branches: [[name: '*/'+env.BRANCH_NAME]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'Newsriver-beamer']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'Newsriver-beamer', url: 'git@github.com:newsriver/Newsriver-beamer.git']]])
  stage 'Write gradle project setting file'
  writeFile file: 'settings.gradle', text: '''include \'Newsriver-lib\'\ninclude \'Newsriver-beamer\''''

  stage 'compile'
  sh 'gradle compileJava -b Newsriver-beamer/build.gradle'

  stage 'test'
  sh 'gradle test -b Newsriver-beamer/build.gradle'

  if(env.BRANCH_NAME=="master"){
    deployDockerImage()
  }
}


def deployDockerImage(){

  stage 'Docker deploy'
  initDocker()

  sh 'gradle clean'
  sh 'gradle fatJar -b Newsriver-beamer/build.gradle'

  dir('Newsriver-beamer/docker'){
    deleteDir()
  }

  sh 'mkdir Newsriver-beamer/docker'

  dir('Newsriver-beamer/docker'){
    sh 'cp ../build/libs/Newsriver-beamer-*.jar .'
    sh 'cp ../Dockerfile .'
    docker.withRegistry('https://docker-registry.newsriver.io:5000/') {
        docker.build('newsriver-beamer:'+env.BUILD_TAG).push('latest')
    }
  }

}


def initDocker(){
  def status = sh(script: 'service docker status', returnStatus: true)
  if(status!=0){
    sh 'service docker start'
  }
}
