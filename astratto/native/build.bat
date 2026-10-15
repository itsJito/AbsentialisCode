@echo off
rem Astratto Engine - builds the native core (astratto.dll) and its own self-test with the Visual C++ compiler.
rem No CMake needed: a CMakeLists.txt sits beside this file for anyone who has it, and for other platforms.
rem   build.bat          build the library and the self-test, then run the self-test
rem   build.bat notest   build only
setlocal enabledelayedexpansion
set HERE=%~dp0
set OUT=%HERE%..\bin
set OBJ=%HERE%build

where cl >nul 2>nul
if not errorlevel 1 goto :compile
set "VSWHERE=%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vswhere.exe"
if not exist "%VSWHERE%" echo astratto: no Visual Studio installation found & exit /b 2
for /f "usebackq delims=" %%i in (`"%VSWHERE%" -latest -products * -property installationPath`) do set "VSROOT=%%i"
if not exist "%VSROOT%\VC\Auxiliary\Build\vcvars64.bat" echo astratto: the C++ build tools are not installed & exit /b 2
call "%VSROOT%\VC\Auxiliary\Build\vcvars64.bat" >nul

:compile
if not exist "%OUT%" mkdir "%OUT%"
if not exist "%OBJ%" mkdir "%OBJ%"

set FLAGS=/nologo /std:c++17 /O2 /EHsc /W4 /fp:precise /MT /DNDEBUG /I"%HERE%include" /I"%HERE%src"
set SRC="%HERE%src\api\c_api.cpp" "%HERE%src\core\engine.cpp" "%HERE%src\world\voxel_world.cpp" "%HERE%src\nav\pathfinder.cpp" "%HERE%src\perception\sight.cpp" "%HERE%src\render\chunk_mesher.cpp"

cl %FLAGS% /DASTRATTO_BUILD /LD %SRC% /Fo"%OBJ%\\" /Fe"%OUT%\astratto.dll" /link /IMPLIB:"%OBJ%\astratto.lib" /PDB:none
if errorlevel 1 ( echo astratto: the library did not build & exit /b 1 )

cl %FLAGS% "%HERE%tests\selftest.cpp" /Fo"%OBJ%\\" /Fe"%OUT%\astratto_selftest.exe" /link "%OBJ%\astratto.lib"
if errorlevel 1 ( echo astratto: the self-test did not build & exit /b 1 )

if /i "%1"=="notest" exit /b 0
"%OUT%\astratto_selftest.exe"
exit /b %errorlevel%
