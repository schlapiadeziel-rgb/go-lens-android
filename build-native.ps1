$ErrorActionPreference = 'Stop'
$sdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { throw 'Set ANDROID_SDK_ROOT or ANDROID_HOME before building KataGo' }
$cmake = Join-Path $sdk 'cmake\3.22.1\bin\cmake.exe'
$ninja = Join-Path $sdk 'cmake\3.22.1\bin\ninja.exe'
$ndk = Join-Path $sdk 'ndk\28.2.13676358'
$root = $PSScriptRoot
& $cmake -S "$root/vendor/KataGo-1.15.3/cpp" -B "$root/native-build" -G Ninja "-DCMAKE_MAKE_PROGRAM=$ninja" "-DCMAKE_TOOLCHAIN_FILE=$ndk/build/cmake/android.toolchain.cmake" -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 -DANDROID_STL=c++_static -DUSE_BACKEND=EIGEN -DNO_GIT_REVISION=1 -DCMAKE_BUILD_TYPE=Release "-DEIGEN3_INCLUDE_DIRS=$root/vendor/eigen-3.4.0" '-DCMAKE_EXE_LINKER_FLAGS=-Wl,-z,max-page-size=16384'
if ($LASTEXITCODE -ne 0) { throw 'Native configure failed' }
& $cmake --build "$root/native-build" --parallel 4
if ($LASTEXITCODE -ne 0) { throw 'Native build failed' }
Copy-Item -LiteralPath "$root/native-build/katago" -Destination "$root/app/src/main/jniLibs/arm64-v8a/libkatago.so" -Force
& "$ndk/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-strip.exe" --strip-unneeded "$root/app/src/main/jniLibs/arm64-v8a/libkatago.so"
if ($LASTEXITCODE -ne 0) { throw 'Native strip failed' }
