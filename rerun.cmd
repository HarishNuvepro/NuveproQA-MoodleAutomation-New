@echo off
REM Re-run failed/skipped scenarios after a module execution (see RUNNERS.md).
setlocal
cd /d "%~dp0"

if "%1"=="" goto usage
if /i "%1"=="list" goto list
if /i "%1"=="generate" goto generate
if /i "%1"=="gen" goto generate
if /i "%1"=="run" goto run
if /i "%1"=="run-parallel" goto runpar
goto usage

:list
py -3 -c "from tools.module_rerun_registry import unique_specs; [print(s.module_id, '-', s.label) for s in unique_specs()]"
exit /b 0

:generate
if "%2"=="" (
  echo Usage: rerun generate ^<module^>  or  rerun generate all
  exit /b 1
)
set PYTHONIOENCODING=utf-8
if /i "%2"=="all" (
  py -3 tools\generate_module_rerun.py --all
) else (
  py -3 tools\generate_module_rerun.py --module %2
)
exit /b %ERRORLEVEL%

:run
if "%2"=="" (
  echo Usage: rerun run ^<module^>
  exit /b 1
)
call :generate %2
if errorlevel 1 exit /b 1
if not exist "reports\rerun\%2_rerun.cmd" (
  echo No rerun file for %2
  exit /b 1
)
call reports\rerun\%2_rerun.cmd
exit /b %ERRORLEVEL%

:runpar
if "%2"=="" (
  echo Usage: rerun run-parallel ^<module^>
  exit /b 1
)
call :generate %2
if errorlevel 1 exit /b 1
if not exist "reports\rerun\%2_rerun_parallel.cmd" (
  echo No parallel rerun file for %2
  exit /b 1
)
call reports\rerun\%2_rerun_parallel.cmd
exit /b %ERRORLEVEL%

:usage
echo.
echo Module re-run helper (after parallel/serial module test):
echo   rerun list
echo   rerun generate users ^| upload_users ^| all_courses ^| sandbox ^| cloudlabs_integration ^| all
echo   rerun run sandbox          ^(serial -Pwatch, stable^)
echo   rerun run-parallel sandbox ^(same tags, -Pparallel^)
echo.
echo Output: reports/rerun/^<module^>_rerun.cmd
echo.
exit /b 0
