/**
 * index.js — "classe main" do React Native.
 *
 * O Expo registra o App.js como a raiz do aplicativo. Este arquivo é o
 * ponto de entrada que o Metro (o "Maven" do RN) procura quando o app sobe.
 */
import { registerRootComponent } from 'expo';
import App from './App';

registerRootComponent(App);