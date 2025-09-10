#!/bin/bash


APP_PACKAGE="ao.co.isptec.aplm.sca"
APP_ACTIVITY=".MainActivity"     

devices=($(adb devices | grep -w "device" | cut -f1))
count=${#devices[@]}

if [ $count -eq 0 ]; then
    echo "Nenhum dispositivo/emulador encontrado. Conecte ou inicie um AVD."
    exit 1
elif [ $count -eq 1 ]; then
    target=${devices[0]}
    echo "Usando dispositivo: $target"
else
    echo "Vários dispositivos encontrados:"
    for i in "${!devices[@]}"; do
        echo "[$i] ${devices[$i]}"
    done

    read -p "Escolha o número do dispositivo: " choice
    target=${devices[$choice]}
    echo "Usando dispositivo: $target"
fi

echo "Compilando APK..."
./gradlew assembleDebug

if [ $? -ne 0 ]; then
  echo "Erro na compilação"
  exit 1
fi

echo "Instalando no dispositivo $target ..."
adb -s $target install -r app/build/outputs/apk/debug/app-debug.apk

if [ $? -ne 0 ]; then
  echo "Erro na instalação (verifique se o dispositivo está acessível)"
  exit 1
fi

echo "Abrindo aplicação..."
adb -s $target shell am start -n $APP_PACKAGE/$APP_ACTIVITY
