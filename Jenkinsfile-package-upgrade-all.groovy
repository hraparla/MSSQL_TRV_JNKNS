import java.nio.file.Paths
import java.nio.file.Path
//PARAMETERS
//Jenkins
def dbmJenkinsNode = "master"
//Version Control System (Git, SVN, etc.)
def rootFolder = "versions"
//def versionToUpgradeTo = "4"
def versionToUpgradeTo = env.VERSION
//DBmaestro
def javaCmd = "java -jar \"C:\\Program Files (x86)\\DBmaestro\\DOP Server\\Agent\\DBmaestroAgent.jar\""
def projectName = "MSSQL_P2"
def server = "t21-jc"
def authType = "DBmaestroAccount"
def username = "poc@dbmaestro.com" //DBmaestro username (user needs "Automation Admin" permission and to be added to "$hostname" and "." as Linked Domains)
def token = "VmzU9NIDff1BALGXgsh58XXIg89FH7U5" //token taken from "My Profile" section inside DBmaestro
def useSSL = "n" //use SSL to communicate with DBmaestro?
def versionFolder = null
def packaged

//RS
def rsEnvName = "RS" //name of Release Source Environment in your DBmaestro Project
def rsEnvPackagesFilePath = "rsEnvPackages.json"
def upgradedRS
//QA
def qaEnvName = "QA" //name of Release Source Environment in your DBmaestro Project
def qaEnvPackagesFilePath = "qaEnvPackages.json"
def upgradedQA
//UAT1
def uat1EnvName = "UAT1" //name of Release Source Environment in your DBmaestro Project
def uat1EnvPackagesFilePath = "uat1EnvPackages.json"
def upgradedUAT1
//UAT2
def uat2EnvName = "UAT2" //name of Release Source Environment in your DBmaestro Project
def uat2EnvPackagesFilePath = "uat2EnvPackages.json"
def upgradedUAT2
//PROD
def prodEnvName = "PROD" //name of Release Source Environment in your DBmaestro Project
def prodEnvPackagesFilePath = "prodEnvPackages.json"
def upgradedPROD

//STAGES
stage("Packaging") {
  node (dbmJenkinsNode) {
    //checkout whole repo if needed, to be able to see package folders
    checkout scm
    //get version from git commit message, if needed            
    //def versionToUpgradeTo = getVersionFromGitCommit() //not getting version from git commit message right now           
    //package only if needed
    versionFolder = getVersionFolder(rootFolder, versionToUpgradeTo)
    packaged = packageIfNeeded(javaCmd, projectName, versionToUpgradeTo, rootFolder, versionFolder, rsEnvName, rsEnvPackagesFilePath, server, authType, username, token, useSSL)
  }
}

stage("DryRun"){
  node (dbmJenkinsNode) {
    dbmPreCheck(javaCmd, projectName, versionFolder, server, authType, username, token, useSSL)
  }
}

stage("RS"){
  node (dbmJenkinsNode) {
    upgradedRS = upgradeIfApplicable(javaCmd, projectName, rsEnvName, versionToUpgradeTo, versionFolder, rsEnvPackagesFilePath, server, authType, username, token, useSSL)
  }
}

stage("QA"){
  node (dbmJenkinsNode) {
    upgradedQA = upgradeIfApplicable(javaCmd, projectName, qaEnvName, versionToUpgradeTo, versionFolder, qaEnvPackagesFilePath, server, authType, username, token, useSSL)
  }
}

stage("UAT"){
  node (dbmJenkinsNode) {
    upgradedUAT1 = upgradeIfApplicable(javaCmd, projectName, uat1EnvName, versionToUpgradeTo, versionFolder, uat1EnvPackagesFilePath, server, authType, username, token, useSSL)
    upgradedUAT2 = upgradeIfApplicable(javaCmd, projectName, uat2EnvName, versionToUpgradeTo, versionFolder, uat2EnvPackagesFilePath, server, authType, username, token, useSSL)
  }
}

    stage ('Approval gate') {
        def message = 'APPROVE RELEASE TO PROD?'
        echo """=======================================================================================================================  
${message}
======================================================================================================================="""
        timeout(time: 30, unit: 'MINUTES') {
            def userInput = input(
                id: 'userInput', message: "$message", parameters: [
                    [$class: 'TextParameterDefinition', defaultValue: 'I approve the deployment', description: 'To proceed, type I approve the deployment', name: 'Review deployment artifacts before proceeding']
                ]
            )
            if (userInput.indexOf('I approve the deployment') == -1) {
                currentBuild.result = 'ABORTED'
                error('Deployment aborted')
            }
        }
    } 

stage("PROD"){
  node (dbmJenkinsNode) {
    upgradedPROD = upgradeIfApplicable(javaCmd, projectName, prodEnvName, versionToUpgradeTo, versionFolder, prodEnvPackagesFilePath, server, authType, username, token, useSSL)
  }
}


def packageIfNeeded(javaCmd, projectName, version, rootFolder, versionFolder, rsEnvName, rsEnvPackagesFilePath, server, authType, username, token, useSSL){
  //get Release Source environment packages
  dbmGetEnvPackages(javaCmd, projectName, rsEnvName, rsEnvPackagesFilePath, server, authType, username, token, useSSL)
  //get latest available version
  def latestAvailableVersion = getLatestAvailableVersion(rsEnvPackagesFilePath)
  def packaged = 0
  if(compareVersions(v1: version, v2: latestAvailableVersion) == 1){
    //echo "Should upload package"
    if(versionFolder != null){
      dbmCreateManifestFile(javaCmd, rootFolder, versionFolder)
      zipPackageFolder(rootFolder, versionFolder)
      dbmPackage(javaCmd, projectName, rootFolder, versionFolder, server, authType, username, token, useSSL)
      packaged = 1 //packaged
    }
    else{
      packaged = -1 //version folder not found
    }
  }
  return packaged
}

def upgradeIfApplicable(javaCmd, projectName, envName, version, packageName, envPackagesFilePath, server, authType, username, token, useSSL){
  def upgraded = 0
  dbmGetEnvPackages(javaCmd, projectName, envName, envPackagesFilePath, server, authType, username, token, useSSL)
  def latestDeployedVersion = getLatestDeployedVersion(envName, envPackagesFilePath)
  if(latestDeployedVersion != null && compareVersions(v1: version, v2: latestDeployedVersion) == 1){
    dbmUpgrade(javaCmd, projectName, envName, packageName, server, authType, username, token, useSSL)
    upgraded = 1
  }
  return upgraded
}

def getLatestAvailableVersion(rsEnvPackagesFilePath){
  //msgbox "Get Latest Available Version"
  def packages = readJSON file: rsEnvPackagesFilePath
  //print "Number of packages: ${packages.size()}"
  def latestPackage = packages[0]
  def version = getPackageVersion(latestPackage.VersionName)
  msgbox "Latest Available Version: ${version}"
  return version
}

def getPackageVersion(packageName){
  def version = packageName.substring(1)
  def descStart = version.indexOf("__")
  if(descStart != -1){
    version = version.substring(0,descStart)
  }
  return version
}

def getLatestDeployedVersion(envName, envPackagesFilePath){
  //msgbox "Get Latest Deployed Version"
  def packages = readJSON file: envPackagesFilePath
  def version = null
  (packages).find{
    //echo "${it}"
    if("${it.EnvDeployed}" != "null"){
      //echo "it's NOT null"
      version = getPackageVersion(it.VersionName)
      return true
    }
    else{
      return false
    }
  }
  msgbox "Latest Deployed Version in ${envName} is ${version}"
  return version
}


def zipPackageFolder(rootFolder, packageFolder){
    def path = "${rootFolder}\\${packageFolder}\\*"
    msgbox "Zipping package folder content (${path})"
    PowerShell("Compress-Archive -Path ${path} -DestinationPath ${rootFolder}\\${packageFolder}.zip -Force")
}

def getVersionFromGitCommit(){
  git_message = bat(
          script: "@git log -1 HEAD --pretty=format:%%s",
          returnStdout: true
        ).trim()
  def pattern = ~/(\d+\.)?(\d+\.)?(\d+\.)?(\d+)/
  def matcher = git_message =~ pattern
  def version = null
  if(matcher.size() != 0){
    version = matcher[0][0]
  }
  if(version == null){
    msgbox "No version found"
  }
  else{
    msgbox "Found version: " + version
  }
  return version
}

def PowerShell(psCmd){
    psCmd=psCmd.replaceAll("%", "%%")
    bat "powershell.exe -NonInteractive -ExecutionPolicy Bypass -Command \"\$ErrorActionPreference='Stop';[Console]::OutputEncoding=[System.Text.Encoding]::UTF8;$psCmd;EXIT \$global:LastExitCode\""
}

def getVersionFolder(rootFolder, version){
  def files
  def globPattern = "V${version}*/**/*.sql"
  def versionFolder = null
  dir("${rootFolder}/") {
    files = findFiles(glob: globPattern)
  }

  //def files = findFiles(glob: '*.*')
  //def files = findFiles(glob: '${rootFolder}/V${version}*')
  //(files).each{
  //  echo """${it.name} ${it.path} ${it.directory} ${it.length} ${it.lastModified}"""
  //}

  //msgbox "Files in ${globPattern} are ${files.size()}"
  if(files.size() > 0){
    def file1 = files[0]
    echo file1.path
    versionFolder = file1.path.substring(0, file1.path.indexOf("\\"))
    msgbox "Version folder is ${versionFolder}'"
  }
  return versionFolder
}

@NonCPS
String mostRecentVersion(List versions) {
  def sorted = versions.sort(false) { a, b -> 

    //echo "a, b: ${a}, ${b}"
      
    List verA = a.tokenize('.')
    List verB = b.tokenize('.')
    //echo "verA: ${verA}" 
    //echo "verB: ${verB}" 
      
    def commonIndices = Math.min(verA.size(), verB.size())
    
    for (int i = 0; i < commonIndices; ++i) {
      def numA = verA[i].toInteger()
      def numB = verB[i].toInteger()
      
      //echo "numA: ${numA}"
      //echo "numB: ${numB}"
        
      if (numA != numB) {
        return numA <=> numB
      }
    }
    
    // If we got this far then all the common indices are identical, so whichever version is longer must be more recent
    verA.size() <=> verB.size()
  }
  
  echo "sorted versions: $sorted"
  sorted[-1]
}

def dbmCreateManifestFile(javaCmd, rootFolder, packageFolder){
  def pathToScriptsFolder = "${rootFolder}\\${packageFolder}"
  msgbox "Creating package.json manifest file for ${pathToScriptsFolder}"
  //-ScriptsOrderScope Global
  bat "${javaCmd} -CreateManifestFile -PathToScriptsFolder ${pathToScriptsFolder} -Operation CreateOrUpdate"
}

def dbmGetEnvPackages(javaCmd, projectName, envName, filePath, server, authType, username, token, useSSL){
  //msgbox "GetEnvPackages of ${envName} and put them in file named '${filePath}'"
  bat "${javaCmd} -GetEnvPackages -ProjectName ${projectName} -EnvName \"${envName}\" -FilePath ${filePath} -Server ${server} -AuthType ${authType} -UserName ${username} -Password ${token} -UseSSL ${useSSL}"
}

def dbmPackage(javaCmd, projectName, rootFolder, packageFolder, server, authType, username, token, useSSL){
  def filePath = "${rootFolder}\\${packageFolder}.zip"
  msgbox "Packaging Files for ${filePath}"
  bat "${javaCmd} -Package -ProjectName ${projectName} -FilePath ${filePath} -Server ${server} -AuthType ${authType} -UserName ${username} -Password ${token} -UseSSL ${useSSL}"
}

def dbmUpgrade(javaCmd, projectName, envName, packageName, server, authType, username, token, useSSL)
{
    msgbox "Upgrading ${envName} to ${packageName}"
    bat "${javaCmd} -Upgrade -ProjectName ${projectName} -EnvName \"${envName}\" -PackageName ${packageName} -Server ${server} -AuthType ${authType} -UserName ${username} -Password ${token} -UseSSL ${useSSL}"
}

def dbmPreCheck(javaCmd, projectName, packageName, server, authType, username, token, useSSL)
{
    msgbox "Performing PreCheck on Package ${packageName}"
    bat "${javaCmd} -PreCheck -ProjectName ${projectName} -PackageName ${packageName} -Server ${server} -AuthType ${authType} -UserName ${username} -Password ${token} -UseSSL ${useSSL}"
}


def msgbox(msg, def mtype = "nosep"){
  def tot = 80
  def start = ""
  def res = ""
  msg = (msg.size() > 65) ? msg[0..64] : msg
  def ilen = tot - msg.size()
  if (mtype == "sep"){
    start = "#${"-" * (ilen/2).toInteger()} ${msg} "
    res = "${start}${"-" * (tot - start.size() + 1)}#"
  }else{
    res = "#${"-" * tot}#\n"
    start = "#${" " * (ilen/2).toInteger()} ${msg} "
    res += "${start}${" " * (tot - start.size() + 1)}#\n"
    res += "#${"-" * tot}#\n"   
  }
  println res
}
