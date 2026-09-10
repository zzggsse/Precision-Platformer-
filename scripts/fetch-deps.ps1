# Downloads the libGDX desktop (LWJGL3) dependencies into libs\.
#
# The dependency set is the union of two POMs, restricted to the natives this
# machine needs (Windows x64):
#   com.badlogicgames.gdx:gdx-backend-lwjgl3:<v>  -> gdx + LWJGL 3.3.3 + jlayer/jorbis
#   com.badlogicgames.gdx:gdx:<v>                 -> gdx-jnigen-loader (SharedLibraryLoader)
# The backend POM does NOT list gdx-jnigen-loader because it arrives transitively
# through gdx itself -- that omission cost a debugging round, so it is explicit here.
# The POMs also list linux / macos / windows-x86 natives; those are skipped on purpose.
#
# To upgrade libGDX: bump $GdxVersion, then run
#   java tools\MavenFetch.java pom com.badlogicgames.gdx:gdx-backend-lwjgl3:<new>
#   java tools\MavenFetch.java pom com.badlogicgames.gdx:gdx:<new>
# and re-check the lists below (especially whether LWJGL / jnigen-loader versions changed).
param(
    [string]$GdxVersion   = '1.14.2',
    [string]$LwjglVersion = '3.3.3',
    [string]$JnigenLoaderVersion = '2.5.2'
)

. "$PSScriptRoot\env.ps1"

$coords = @(
    # libGDX core
    "com.badlogicgames.gdx:gdx:$GdxVersion"
    "com.badlogicgames.gdx:gdx-platform:${GdxVersion}:natives-desktop"
    "com.badlogicgames.gdx:gdx-backend-lwjgl3:$GdxVersion"

    # transitive dep of gdx: provides com.badlogic.gdx.utils.SharedLibraryLoader
    "com.badlogicgames.gdx:gdx-jnigen-loader:$JnigenLoaderVersion"

    # LWJGL3 runtime (compile deps of the backend POM)
    "org.lwjgl:lwjgl:$LwjglVersion"
    "org.lwjgl:lwjgl:${LwjglVersion}:natives-windows"
    "org.lwjgl:lwjgl-glfw:$LwjglVersion"
    "org.lwjgl:lwjgl-glfw:${LwjglVersion}:natives-windows"
    "org.lwjgl:lwjgl-jemalloc:$LwjglVersion"
    "org.lwjgl:lwjgl-jemalloc:${LwjglVersion}:natives-windows"
    "org.lwjgl:lwjgl-openal:$LwjglVersion"
    "org.lwjgl:lwjgl-openal:${LwjglVersion}:natives-windows"
    "org.lwjgl:lwjgl-opengl:$LwjglVersion"
    "org.lwjgl:lwjgl-opengl:${LwjglVersion}:natives-windows"
    "org.lwjgl:lwjgl-stb:$LwjglVersion"
    "org.lwjgl:lwjgl-stb:${LwjglVersion}:natives-windows"

    # audio decoding (mp3 / ogg)
    "com.badlogicgames.jlayer:jlayer:1.0.1-gdx"
    "org.jcraft:jorbis:0.0.17"
)

New-Item -ItemType Directory -Force -Path $LibsDir | Out-Null

Write-Host "JDK  : $Jdk"
Write-Host "libs : $LibsDir"
Write-Host ""

& $Java "$ToolDir\MavenFetch.java" fetch $LibsDir @coords
exit $LASTEXITCODE
