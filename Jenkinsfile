pipeline {
  agent any
  options {
    timestamps()
    timeout(time: 3600, unit: 'SECONDS')
  }
  parameters {
    string(name: 'CREATE_RELEASE', defaultValue: 'false')
    string(name: 'VERSION', defaultValue: 'deleteme')
    string(name: 'REPO_URL', defaultValue: '')
    string(name: 'BROWSER', defaultValue: 'htmlunit')
    string(name: 'SKIP_TESTS', defaultValue: 'false')
  }
  environment{
    APP="keycloak-event-emitter"
  }

  stages {
    stage('Build') {
      agent {
        label 'jenkins-slave-maven-ct'
      }
      steps {
        script {
          sh 'printenv'
          def options = ""
          def prefix = ""
          if (params.BROWSER == "chrome") {
            options = '-DchromeOptions="--headless --no-sandbox --disable-setuid-sandbox --disable-gpu --disable-software-rasterizer --remote-debugging-port=9222 --disable-infobars"'
            prefix = 'xvfb-run --server-args="-screen 0 1920x1080x24" --server-num=99'
          } else if (params.BROWSER == "firefox") {
            options = '-DchromeOptions="-headless"'
            prefix = 'xvfb-run --server-args="-screen 0 1920x1080x24" --server-num=99'
          }
          sh """
            ${prefix} mvn -T4 -B clean package \
              -Dbrowser=\"${params.BROWSER}\" \
              ${options} \
              -DskipTests=${params.SKIP_TESTS} \
              spotbugs:spotbugs pmd:pmd \
              -Dsonar.java.spotbugs.reportPaths=event-emitter/target/spotbugsXml.xml \
              -Dsonar.java.pmd.reportPaths=event-emitter/target/pmd.xml \
              sonar:sonar
          """
          if (params.CREATE_RELEASE == "true"){
            echo "creating release ${VERSION} and uploading it to ${REPO_URL}"
            // upload to repo
            withCredentials([usernamePassword(credentialsId: 'cloudtrust-cicd-artifactory-opaque', usernameVariable: 'USR', passwordVariable: 'PWD')]){
              sh """
                cd target
                mv "${APP}"-?.?.?*.tar.gz "${APP}-${params.VERSION}.tar.gz"
                curl -k -u"${USR}:${PWD}" -T "${APP}-${params.VERSION}.tar.gz" --keepalive-time 2 "${REPO_URL}/${APP}-${params.VERSION}.tar.gz"
              """
            }
            def git_url = "${env.GIT_URL}".replaceFirst("^(http[s]?://www\\.|http[s]?://|www\\.)","")
            withCredentials([usernamePassword(credentialsId: "3d6daa6f-8eea-43d0-b69e-0616258d5b1b",
                passwordVariable: 'PWD',
                usernameVariable: 'USR')]) {
              sh("git config --global user.email 'ci@dev.null'")
              sh("git config --global user.name 'ci'")
              sh("git tag ${VERSION} -m 'CI'")
              sh("git push https://${USR}:${PWD}@${git_url} --tags")
            }
            echo "release ${VERSION} available at ${REPO_URL}/${APP}-${params.VERSION}.tar.gz"
          }
        }
      }
    }
  }
}
