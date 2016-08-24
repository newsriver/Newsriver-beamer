node {
  stage 'Checkout Library'
  checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'Newsriver-lib']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'newsriver-lib', url: 'git@github.com:newsriver/Newsriver-lib.git']]])
  stage 'Checkout Beamer'
  checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'Newsriver-beamer']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'Newsriver-beamer', url: 'git@github.com:newsriver/Newsriver-beamer.git']]])
  stage 'Write gradle project setting file'
  writeFile file: 'settings.gradle', text: '''include \'Newsriver-lib\'\ninclude \'Newsriver-beamer\''''



  stage 'Build fatjat'
  sh 'gradle fatJar -b Newsriver-beamer/build.gradle'
  stage 'Build Docker image'
  dir('Newsriver-beamer/docker'){
    deleteDir()
  }
  sh 'mkdir Newsriver-beamer/docker'
  dir('Newsriver-beamer/docker'){
    sh 'service docker start'
    sh 'cp ../build/libs/Newsriver-beamer-*.jar .'
    sh 'cp ../Dockerfile .'
    sh 'docker build -t docker-registry.newsriver.io:5000/newsriver-beamer:latest .'
    sh 'docker push  docker-registry.newsriver.io:5000/newsriver-beamer'
  }


}
