#ifndef MyAppName
  #define MyAppName "ClipBridge"
#endif

#ifndef MyAppVersion
  #define MyAppVersion "1.0.0"
#endif

#ifndef MyAppPublisher
  #define MyAppPublisher "ClipBridge"
#endif

#ifndef MyAppImageDir
  #define MyAppImageDir "..\dist\app-image\ClipBridge"
#endif

#ifndef MySetupIcon
  #define MySetupIcon "..\src\main\resources\icons\icon.ico"
#endif

[Setup]
AppId={{C6B10710-2A68-4BE4-A7F8-60356C9F8431}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={localappdata}\Programs\{#MyAppName}
DisableProgramGroupPage=yes
OutputDir=..\dist
OutputBaseFilename={#MyAppName}-{#MyAppVersion}
Compression=lzma
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=lowest
UsedUserAreasWarning=no
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
UninstallDisplayIcon={app}\{#MyAppName}.exe
SetupIconFile={#MySetupIcon}

[Languages]
Name: "chinesesimp"; MessagesFile: "compiler:Default.isl"

[Files]
Source: "{#MyAppImageDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\{#MyAppName}.exe"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppName}.exe"

[Run]
Filename: "{app}\{#MyAppName}.exe"; Description: "启动 {#MyAppName}"; Flags: nowait postinstall skipifsilent


