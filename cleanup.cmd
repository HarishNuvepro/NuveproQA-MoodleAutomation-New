@echo off
REM On-demand automation cleanup (never part of default mvnw test).
REM Run AFTER a green module run. See docs\CLEANUP.md and RUNNERS.md.
REM
REM Usage:
REM   cleanup              same as: cleanup all
REM   cleanup dry          dry-run full cleanup (log only, no Moodle changes)
REM   cleanup all
REM   cleanup core       users + all-courses + sandbox (after Users/Upload/ACP/Sandbox runs)
REM   cleanup users | sandbox | assessment | practice | all-courses | lab-templates | settings
REM
REM Module tests with deferred @After deletes:
REM   mvnw test -Pwatch -Dheadless=false -Dautomation.deferCleanup=true "-Dcucumber.tags=..."
cd /d "%~dp0"

set MVN=.\mvnw.cmd test -Pcleanup
set WATCH=-Pwatch -Dheadless=false
set DRY=-DcleanupDryRun=true

if "%1"=="" goto all
if /i "%1"=="help" goto help
if /i "%1"=="/?" goto help
if /i "%1"=="dry" goto dry_all
if /i "%1"=="all" goto all
if /i "%1"=="core" goto core
if /i "%1"=="users" goto users
if /i "%1"=="sandbox" goto sandbox
if /i "%1"=="assessment" goto assessment
if /i "%1"=="practice" goto practice
if /i "%1"=="all-courses" goto allcourses
if /i "%1"=="lab-templates" goto labtemplates
if /i "%1"=="settings" goto settings
echo [cleanup] Unknown argument: %1
goto help

:help
echo.
echo cleanup [dry^|all^|core^|users^|sandbox^|assessment^|practice^|all-courses^|lab-templates^|settings]
echo.
exit /b 1

:dry_all
echo [cleanup] DRY-RUN — nothing will be deleted or restored.
call %MVN% %WATCH% %DRY% "-Dcucumber.tags=@cleanup-all"
exit /b %ERRORLEVEL%

:all
echo [cleanup] Full cleanup (@cleanup-all)
call %MVN% %WATCH% "-Dcucumber.tags=@cleanup-all"
exit /b %ERRORLEVEL%

:core
echo [cleanup] Users + Upload users manifest + All Courses + Sandbox (@cleanup-users-all-courses-sandbox)
call %MVN% %WATCH% "-Dcucumber.tags=@cleanup-users-all-courses-sandbox"
exit /b %ERRORLEVEL%

:users
call %MVN% %WATCH% "-Dcucumber.tags=@cleanup-users"
exit /b %ERRORLEVEL%

:sandbox
call %MVN% %WATCH% "-Dcucumber.tags=@sandbox-cleanup"
exit /b %ERRORLEVEL%

:assessment
call %MVN% %WATCH% "-Dcucumber.tags=@cleanup-assessment"
exit /b %ERRORLEVEL%

:practice
call %MVN% %WATCH% "-Dcucumber.tags=@cleanup-practice"
exit /b %ERRORLEVEL%

:allcourses
call %MVN% %WATCH% "-Dcucumber.tags=@cleanup-all-courses"
exit /b %ERRORLEVEL%

:labtemplates
call %MVN% %WATCH% "-Dcucumber.tags=@cleanup-lab-templates"
exit /b %ERRORLEVEL%

:settings
call %MVN% %WATCH% "-Dcucumber.tags=@settings-cleanup"
exit /b %ERRORLEVEL%
