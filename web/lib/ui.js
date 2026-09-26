import {escape as e, clamp} from './format.js';
import {setWorkspaceKey, workspaceKey} from './api.js';
const paths = {
  pulse:'M3 12h4l3-8 4 16 3-8h4', grid:'M3 3h7v7H3zM14 3h7v7h-7zM3 14h7v7H3zM14 14h7v7h-7z',
  flask:'M9 3h6M10 3v7l-6 9a1 1 0 0 0 1 2h14a1 1 0 0 0 1-2l-6-9V3M8 14h8',
  database:'M20 6c0 2-4 3-8 3S4 8 4 6s4-3 8-3 8 1 8 3ZM4 6v12c0 2 4 3 8 3s8-1 8-3V6M4 12c0 2 4 3 8 3s8-1 8-3',
  code:'m8 7-5 5 5 5m8-10 5 5-5 5m-3-13-2 16', check:'m5 12 4 4L19 6',
  clock:'M12 8v5l3 2M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z',
  plus:'M12 5v14M5 12h14', arrow:'M5 12h14m-5-5 5 5-5 5', chevron:'m9 5 7 7-7 7',
  download:'M12 3v12m-5-5 5 5 5-5M4 16v5h16v-5', close:'m6 6 12 12M18 6 6 18',
  settings:'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8ZM12 2v3m0 14v3M2 12h3m14 0h3M5 5l2 2m10 10 2 2M5 19l2-2M17 7l2-2',
  branch:'M6 3v12a5 5 0 0 0 5 5h1M18 6v4a5 5 0 0 1-5 5H6M9 6a3 3 0 1 1-6 0 3 3 0 0 1 6 0ZM21 6a3 3 0 1 1-6 0 3 3 0 0 1 6 0ZM18 20a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z',
  file:'M14 2H5v20h14V7l-5-5ZM14 2v5h5M8 12h8M8 16h6',
  shield:'m12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6l8-3ZM8 12l3 3 5-6',
  search:'M21 21l-6-6M17 10a7 7 0 1 1-14 0 7 7 0 0 1 14 0Z',
  graph:'M7 7l10 10M7 17 17 7M5 10v4M10 5h4M19 10v4M10 19h4M8 5a3 3 0 1 1-6 0 3 3 0 0 1 6 0ZM22 5a3 3 0 1 1-6 0 3 3 0 0 1 6 0ZM8 19a3 3 0 1 1-6 0 3 3 0 0 1 6 0ZM22 19a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z',
  book:'M3 3h8v18H3zM13 3h8v18h-8zM6 7h2M16 7h2', info:'M12 11v6M12 7v1M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z',
  layers:'m12 3 10 5-10 5L2 8l10-5ZM2 12l10 5 10-5M2 16l10 5 10-5', play:'m8 4 12 8-12 8Z',
  trash:'M3 6h18M9 6V3h6v3M6 6l1 15h10l1-15M10 10v7M14 10v7', refresh:'M20 7v5h-5M4 17v-5h5M5 8a8 8 0 0 1 14-3l1 2M4 17l1 2a8 8 0 0 0 14-3',
};
export function icon(name, size=18) {return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="${paths[name] || paths.file}"/></svg>`;}
export function pill(label, kind='neutral') {return `<span class="pill ${e(kind)}">${e(label)}</span>`;}
export function status(value) {return pill(String(value).toLowerCase().replaceAll('_',' '), value==='SUCCEEDED'?'success':value==='FAILED'?'danger':value==='CANCELLED'?'neutral':'pending');}
export function empty(title, text) {return `<div class="empty">${icon('layers',32)}<h3>${e(title)}</h3><p>${e(text)}</p></div>`;}
export function meter(value, className='') {return `<span class="meter ${className}"><i style="width:${clamp(value)*100}%"></i></span>`;}
export function toast(message, isError=false) {
  const element=document.createElement('div');element.className=`toast ${isError?'error':''}`;element.textContent=message;
  document.querySelector('#toasts').append(element);setTimeout(()=>element.remove(), isError?9000:4500);
}
export function report(error) {toast(`${error.message || error}${error.requestId ? ` · request ${error.requestId.slice(0,8)}`:''}`,true);}
export function modal(title, body, onSubmit, {wide=false,submit='Save',description=''}={}) {
  const dialog=document.createElement('dialog');dialog.className=wide?'modal wide':'modal';
  dialog.innerHTML=`<form><header><div><h2>${e(title)}</h2>${description?`<p>${e(description)}</p>`:''}</div><button type="button" class="icon-button" data-close aria-label="Close dialog">${icon('close')}</button></header><section class="modal-body">${body}<p class="form-error" role="alert"></p></section><footer><button type="button" class="button ghost" data-close>Cancel</button><button class="button primary" type="submit">${e(submit)}</button></footer></form>`;
  document.body.append(dialog);dialog.querySelectorAll('[data-close]').forEach(button=>button.addEventListener('click',()=>dialog.close()));
  dialog.addEventListener('close',()=>dialog.remove());
  dialog.querySelector('form').addEventListener('submit',async event=>{
    event.preventDefault();const button=dialog.querySelector('[type=submit]');button.disabled=true;dialog.querySelector('.form-error').textContent='';
    try {const result=await onSubmit(new FormData(event.target),dialog);if(result!==false)dialog.close();}
    catch(error){dialog.querySelector('.form-error').textContent=error.message;}
    finally{button.disabled=false;}
  });
  dialog.showModal();return dialog;
}
export function connectionDialog(reload) {
  modal('Workspace connection',`<p class="muted">Local mode needs no key. A configured workspace key is kept only in this browser tab’s session storage.</p><label>Workspace key<input name="key" type="password" autocomplete="off" value="${e(workspaceKey())}" placeholder="APP_TOKEN, when configured"></label>`,async data=>{setWorkspaceKey(String(data.get('key')||''));await reload();},{submit:'Connect'});
}
export async function readTextFile(file, maximum=1000000) {
  if(!file)throw new Error('Choose a file first.');
  if(file.size>maximum)throw new Error(`File exceeds ${maximum.toLocaleString()} bytes.`);
  return file.text();
}
export function formNumber(data,key,fallback) {const raw=data.get(key);return raw===null||raw===''?fallback:Number(raw);}
