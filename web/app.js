import {api, download} from './lib/api.js';
import {escape as e, percent, integer, decimal, date, shortId, pretty, terminal} from './lib/format.js';
import {icon, pill, status, meter, empty, modal, report, toast, connectionDialog, formNumber, readTextFile} from './lib/ui.js';

const state = {tab:'overview', runs:[], datasets:[], prompts:[], jobs:[], audit:[], meta:{}, run:null, selectedCase:null, filter:'all'};
const app = document.querySelector('#app');
let refreshing = false;
const navigation = [['overview','grid','Overview'],['experiments','flask','Experiments'],['datasets','database','Datasets'],['prompts','code','Prompt lab'],['activity','clock','Activity']];

async function load() {
  const [meta,runs,datasets,prompts,jobs] = await Promise.all(['/api/meta','/api/runs','/api/datasets','/api/prompts','/api/jobs'].map(path=>api(path)));
  Object.assign(state,{meta,runs,datasets,prompts,jobs});
  if(state.run) state.run=await api(`/api/runs/${state.run.id}`);
  if(state.tab==='activity')state.audit=await api('/api/audit');
  render();
}
function render() {
  const active = state.jobs.filter(job=>!terminal(job.status)).length;
  app.innerHTML=`<div class="layout"><aside class="sidebar"><a class="brand" href="/">${icon('pulse',27)}<span>trace<span class="brand-light">pilot</span></span></a>
    <div class="workspace"><span class="workspace-icon">N</span><div>Northstar lab<small>LOCAL WORKSPACE</small></div><span class="workspace-dot"></span></div>
    <span class="nav-label">WORKBENCH</span><nav>${navigation.map(([id,glyph,label])=>`<button data-tab="${id}" class="nav-item ${state.tab===id?'active':''}">${icon(glyph)}<span>${label}</span>${id==='experiments'?`<b>${state.runs.length}</b>`:''}</button>`).join('')}</nav>
    <div class="sidebar-bottom"><div class="engine-state"><i></i> Java engine connected<small>${e(state.meta.storage||'connecting')} persistence</small></div><button class="nav-item" data-action="connection">${icon('settings')}<span>Connection</span></button><div class="version-note">v0.1.0 · single workspace</div></div></aside>
    <div class="workspace-main"><header class="topbar"><span class="breadcrumb">WORKSPACE <span>/</span> ${e(navigation.find(n=>n[0]===state.tab)?.[2].toUpperCase()||'OVERVIEW')}</span><div class="top-actions">${active?pill(`${active} job${active===1?'':'s'} running`,'pending'):pill('Engine ready','success')}<span class="avatar">RV</span></div></header>
    <main class="main-content">${state.run&&state.tab==='experiments'?runPage():state.tab==='overview'?overview():state.tab==='experiments'?experiments():state.tab==='datasets'?datasets():state.tab==='prompts'?prompts():activity()}</main></div></div>${inspector()}`;
}
function pageHeading(eyebrow,title,description,action='') {
  return `<div class="page-heading"><div><p class="eyebrow">${e(eyebrow)}</p><h1>${e(title)}</h1><p class="subtitle">${e(description)}</p></div>${action}</div>`;
}
function runButton(){return `<button class="button primary" data-action="new-run">${icon('plus')} New experiment</button>`;}
function overview(){
  const baseline=state.runs.find(r=>r.provider==='recorded')||state.runs[0];
  const summary=baseline?.summary||{};
  const totalCases=state.datasets.reduce((sum,d)=>sum+d.caseCount,0);
  return `${pageHeading('EVALUATION COMMAND CENTER','Know what changed.','Measure response behavior before it becomes a production surprise.',runButton())}
    <div class="overview-strip"><span>${icon('shield')} Every score has evidence.</span><p>Versioned inputs. Reproducible checks. Human review.</p>${pill('No model key required')}</div>
    <section class="metrics-grid"><article class="metric-card"><span>Baseline pass rate</span><strong>${percent(summary.passRate||0)}</strong>${meter(summary.passRate||0)}<small>${integer(summary.passed)} of ${integer(summary.expected)} cases pass configured checks</small></article>
    <article class="metric-card"><span>Evaluation cases</span><strong>${integer(totalCases)}<em>cases</em></strong><div class="metric-icon">${icon('database',26)}</div><small>Across ${state.datasets.length} versioned dataset${state.datasets.length===1?'':'s'}</small></article>
    <article class="metric-card"><span>p95 processing time</span><strong>${decimal(summary.p95Ms)}<em>ms</em></strong><div class="mini-signal"><i></i><i></i><i></i><i></i><i></i><i></i><i></i></div><small>Measured locally · not a hosted-model benchmark</small></article>
    <article class="metric-card"><span>Cases needing review</span><strong class="amber">${integer(summary.failed)}</strong><span class="metric-icon amber">${icon('flask',26)}</span><small>Automated failures in the recorded baseline</small></article></section>
    <div class="overview-columns"><section class="panel run-panel"><div class="panel-heading"><div><h2>Experiment ledger</h2><p>Compare behavior, not just model names.</p></div><button class="text-button" data-tab="experiments">View all ${icon('arrow',16)}</button></div>${runTable(state.runs.slice(0,5))}</section>
    <section class="panel gate-panel"><div class="panel-heading"><div><h2>Release gate</h2><p>Recorded candidate baseline</p></div>${icon('shield',22)}</div><div class="gate-state ${summary.gatePassed?'passed':'blocked'}">${icon(summary.gatePassed?'check':'info',26)}<div>${summary.gatePassed?'Checks passed':'Review required'}<small>Against this run’s configured thresholds</small></div></div><div class="gate-row"><span>Pass-rate threshold</span><b>${percent(baseline?.settings?.minPassRate||.8)}</b></div><div class="gate-row"><span>Provider failures</span><b>${integer(summary.errors)}</b></div><div class="gate-row"><span>Dataset snapshot</span><b>v${baseline?.datasetVersion||1}</b></div><div class="gate-row"><span>Reference token F1</span><b>${percent(summary.averages?.tokenF1||0)}</b></div><p class="fine-print">These are heuristic quality gates, not a factuality guarantee. Inspect the underlying response and source context.</p></section></div>
    <section class="method-panel"><div class="method-icon">${icon('layers',24)}</div><div><h3>A small benchmark. A visible trail.</h3><p>The Atlas dataset is fictional. Recorded responses contain intentional errors; both sample experiments are evaluated by the Java engine when the workspace starts.</p></div><button class="button ghost" data-tab="datasets">Explore dataset ${icon('arrow',16)}</button></section>`;
}
function runTable(runs){
  if(!runs.length)return empty('No experiments yet','Create a dataset and run your first evaluation.');
  return `<div class="table-scroll"><table><thead><tr><th>EXPERIMENT</th><th>PROVIDER</th><th>STATUS</th><th>PASS RATE</th><th>p95</th><th></th></tr></thead><tbody>${runs.map(run=>`<tr data-open-run="${e(run.id)}" tabindex="0"><td><strong>${e(run.name)}</strong><small>${e(run.datasetName)} · v${run.datasetVersion}</small></td><td><span class="code-label">${e(run.provider)}</span></td><td>${status(run.status)}</td><td><div class="table-rate">${meter(run.summary.passRate)}<b>${percent(run.summary.passRate)}</b></div></td><td class="mono">${decimal(run.summary.p95Ms)} ms</td><td>${icon('chevron',15)}</td></tr>`).join('')}</tbody></table></div>`;
}
function experiments(){return `${pageHeading('EXPERIMENTS','Evidence over intuition.','Immutable snapshots make side-by-side comparisons meaningful.',runButton())}<section class="panel">${runTable(state.runs)}</section>
  <section class="panel compare-panel"><div class="panel-heading"><div><h2>Paired comparison</h2><p>Both runs must use the same dataset version.</p></div>${icon('branch',22)}</div><form id="compare-form"><label>Baseline<select name="baselineId">${state.runs.map(r=>`<option value="${e(r.id)}">${e(r.name)}</option>`).join('')}</select></label><label>Candidate<select name="candidateId">${state.runs.map((r,i)=>`<option value="${e(r.id)}" ${i===1?'selected':''}>${e(r.name)}</option>`).join('')}</select></label><button class="button primary" type="submit">Compare runs ${icon('arrow',16)}</button></form><div id="comparison-output"></div></section>`;}
function runPage(){
  const run=state.run,summary=run.summary;
  const rows=run.results.filter(result=>state.filter==='all'||(state.filter==='failed'&&!result.passed)||(state.filter==='passed'&&result.passed));
  return `<button class="text-button back-button" data-action="back-runs">← All experiments</button>${pageHeading(`EXPERIMENT / ${shortId(run.id)}`,run.name,`${run.datasetName} · dataset v${run.datasetVersion} · prompt v${run.promptVersion}`,`<div class="button-row"><button class="button ghost" data-action="export-csv">${icon('download')} CSV</button><button class="button ghost" data-action="export-json">JSON</button>${!terminal(run.status)?'<button class="button danger-button" data-action="cancel-run">Cancel</button>':''}</div>`)}
    <div class="run-status-bar">${status(run.status)}<span>${e(run.provider)} provider</span><span>${integer(summary.completed)} / ${integer(summary.expected)} cases</span>${run.jobError?`<span class="amber">${e(run.jobError)}</span>`:''}</div>
    <div class="metrics-grid compact"><article class="metric-card"><span>Pass rate</span><strong>${percent(summary.passRate)}</strong>${meter(summary.passRate)}</article><article class="metric-card"><span>Reference token F1</span><strong>${percent(summary.averages.tokenF1||0)}</strong><small>Lexical reference overlap</small></article><article class="metric-card"><span>Context token support</span><strong>${percent(summary.averages.contextSupport||0)}</strong><small>Not hallucination detection</small></article><article class="metric-card"><span>Output tokens</span><strong>${integer(summary.outputTokens)}</strong><small>${run.provider==='ollama'?'Provider-reported':'Word estimate, not model tokenizer'}</small></article></div>
    <section class="panel"><div class="panel-heading"><div><h2>Case-level evidence</h2><p>Select any row to inspect the answer and annotate it.</p></div><select id="result-filter" aria-label="Filter results"><option value="all" ${state.filter==='all'?'selected':''}>All results</option><option value="failed" ${state.filter==='failed'?'selected':''}>Failed checks</option><option value="passed" ${state.filter==='passed'?'selected':''}>Passed checks</option></select></div><div class="table-scroll"><table><thead><tr><th>CASE</th><th>QUESTION</th><th>RESULT</th><th>TOKEN F1</th><th>SUPPORT</th><th>LATENCY</th><th>HUMAN REVIEW</th></tr></thead><tbody>${rows.map(result=>`<tr data-case="${e(result.caseId)}" tabindex="0"><td class="mono muted">${e(result.caseId)}</td><td class="question-cell">${e(result.question)}</td><td>${pill(result.passed?'pass':'review',result.passed?'success':'danger')}</td><td>${result.scores.tokenF1===undefined?'—':percent(result.scores.tokenF1)}</td><td>${result.scores.contextSupport===undefined?'—':percent(result.scores.contextSupport)}</td><td class="mono">${integer(result.latencyMs)} ms</td><td>${pill(result.annotation.verdict)}</td></tr>`).join('')}</tbody></table></div>${!rows.length?empty('No matching results','Try another filter, or wait for queued work to complete.'):''}</section>
    <p class="fine-print">Gate failures: ${e(summary.gateFailures.join(', ')||'none')}. Recorded/extractive latency measures local processing only. Human annotations do not overwrite automated scores.</p>`;
}
function inspector(){
  if(!state.selectedCase||!state.run)return '';
  const row=state.run.results.find(r=>r.caseId===state.selectedCase);if(!row)return '';
  return `<div class="inspector-shade" data-action="close-inspector"></div><aside class="inspector" aria-label="Case evidence"><header><div><p class="eyebrow">CASE INSPECTOR</p><h2>${e(row.caseId)}</h2></div><button class="icon-button" data-action="close-inspector" aria-label="Close case inspector">${icon('close')}</button></header><div class="inspector-body"><h3>${e(row.question)}</h3>${pill(row.passed?'Automated checks passed':'Automated checks failed',row.passed?'success':'danger')}<h4>Candidate response</h4><pre class="evidence response">${e(row.response||row.error||'(empty)')}</pre><h4>Reference answer</h4><pre class="evidence">${e(row.reference||'No reference supplied')}</pre><h4>Source context</h4><pre class="evidence">${e(row.context||'No context supplied')}</pre><h4>Evaluators</h4>${row.metrics.map(m=>`<div class="evaluator-row"><div><b>${e(m.name)}</b><small>${e(m.explanation)}</small></div><span>${m.applicable?percent(m.value):'N/A'}</span></div>`).join('')}<h4>Human annotation</h4><form id="annotation-form"><label>Verdict<select name="verdict">${['unreviewed','pass','fail'].map(v=>`<option ${row.annotation.verdict===v?'selected':''}>${v}</option>`).join('')}</select></label><label>Review notes<textarea name="note" rows="4" maxlength="2000" placeholder="Explain what the checks missed…">${e(row.annotation.note)}</textarea></label><button class="button primary" type="submit">Save annotation</button></form></div></aside>`;
}
function datasets(){return `${pageHeading('DATASETS','A benchmark you can inspect.','Edit cases, preserve version history, and keep old experiment inputs unchanged.',`<button class="button primary" data-action="new-dataset">${icon('plus')} New dataset</button>`)}<div class="dataset-grid">${state.datasets.map(d=>`<article class="panel dataset-card"><div class="card-top"><span class="square-icon">${icon('database',25)}</span>${pill(`VERSION ${d.version}`)}</div><h2>${e(d.name)}</h2><p>${e(d.description)}</p><div class="dataset-stats"><strong>${d.caseCount}<span>evaluation cases</span></strong><span class="muted">Updated ${date(d.updatedAt)}</span></div><button class="button ghost" data-edit-dataset="${e(d.id)}">Inspect & edit cases ${icon('arrow',16)}</button></article>`).join('')}</div><section class="method-panel"><div>${icon('info',24)}</div><p>Each case supports a question, context, reference, recorded response, required phrases, expected format, and optional refusal expectation. Use the JSON editor to import an entire reviewed dataset.</p></section>`;}
function prompts(){return `${pageHeading('PROMPT LAB','Prompts with a paper trail.','A new revision never changes the prompt inside an existing experiment.',`<button class="button primary" data-action="new-prompt">${icon('plus')} New prompt</button>`)}<div class="prompt-grid">${state.prompts.map(p=>`<section class="panel prompt-card"><div class="panel-heading"><div><h2>${e(p.name)}</h2><p>Updated ${date(p.updatedAt)}</p></div>${pill(`v${p.version}`)}</div><h4>SYSTEM INSTRUCTION</h4><p class="system-text">${e(p.system)}</p><h4>PROMPT TEMPLATE</h4><pre class="template-code">${e(p.template)}</pre><footer><span class="muted">Supported fields: {{question}}, {{context}}</span><button class="button ghost" data-edit-prompt="${e(p.id)}">Edit revision ${icon('arrow',16)}</button></footer></section>`).join('')}</div>`;}
function activity(){return `${pageHeading('OPERATIONS','Every run has a trail.','Queued work, cancellation, and metadata-only change history.')}<section class="panel"><div class="panel-heading"><h2>Job queue</h2>${pill('2 workers')}</div><div class="table-scroll"><table><thead><tr><th>JOB</th><th>STATE</th><th>PROGRESS</th><th>CREATED</th><th></th></tr></thead><tbody>${state.jobs.map(j=>`<tr><td><strong>${e(j.kind)}</strong><small class="mono">${shortId(j.id)}</small></td><td>${status(j.status)}</td><td>${j.completed} / ${j.total||'—'}<small>${e(j.error||j.message)}</small></td><td>${date(j.createdAt)}</td><td>${!terminal(j.status)?`<button class="button ghost small" data-cancel-job="${e(j.id)}">Cancel</button>`:''}</td></tr>`).join('')}</tbody></table></div></section><section class="panel audit-panel"><div class="panel-heading"><h2>Audit events</h2><span class="muted">No prompts or responses stored in the audit trail</span></div>${state.audit.slice(0,25).map(a=>`<div class="audit-row"><i></i><strong>${e(a.action)}</strong><code>${e(shortId(a.target))}</code><time>${date(a.at)}</time></div>`).join('')}</section>`;}

function newRun(){
  if(!state.datasets.length||!state.prompts.length){toast('Create a dataset and a prompt first.',true);return;}
  modal('New experiment',`<div class="form-grid"><label class="span-2">Experiment name<input name="name" required maxlength="120" value="Support regression experiment"></label><label>Dataset<select name="datasetId">${state.datasets.map(d=>`<option value="${e(d.id)}">${e(d.name)} · v${d.version}</option>`).join('')}</select></label><label>Prompt<select name="promptId">${state.prompts.map(p=>`<option value="${e(p.id)}">${e(p.name)} · v${p.version}</option>`).join('')}</select></label><label class="span-2">Response provider<select name="provider"><option value="recorded">Recorded — evaluate responses already in the dataset</option><option value="extractive">Extractive — local sentence-selection baseline, not an LLM</option><option value="ollama" ${state.meta.generationConfigured?'':'disabled'}>Ollama — ${e(state.meta.generationModel||'configure OLLAMA_MODEL first')}</option></select></label><label>Minimum token F1<input name="minF1" type="number" min="0" max="1" step=".05" value=".45"></label><label>Minimum context support<input name="minSupport" type="number" min="0" max="1" step=".05" value=".55"></label><label>Minimum pass rate<input name="minPassRate" type="number" min="0" max="1" step=".05" value=".8"></label><label>Maximum latency per case (ms)<input name="maxLatencyMs" type="number" min="1" max="120000" value="3000"></label></div><p class="fine-print">The dataset and prompt are snapshotted at submission. Heuristic scores are reproducible, but do not prove factual correctness.</p>`,async data=>{
    const input=Object.fromEntries(data);for(const key of ['minF1','minSupport','minPassRate','maxLatencyMs'])input[key]=formNumber(data,key,0);
    const job=await api('/api/runs',{method:'POST',body:input,idempotency:crypto.randomUUID()});state.run=await api(`/api/runs/${job.runId}`);state.tab='experiments';state.selectedCase=null;toast('Experiment queued.');await load();
  },{wide:true,submit:'Queue experiment'});
}
async function editDataset(id){
  const original=id?await api(`/api/datasets/${id}`):{name:'My evaluation dataset',description:'Reviewed evaluation cases',cases:[{id:'case-01',question:'How long are backups retained?',context:'Backups are retained for 14 days.',reference:'Backups are retained for 14 days.',recordedResponse:'Backups are retained for 14 days.',requiredPhrases:['14 days'],format:'text',expectRefusal:false}]};
  const payload={name:original.name,description:original.description,cases:original.cases};
  const dialog=modal(id?'Edit dataset · new version':'Create dataset',`<label>Dataset JSON<textarea class="json-editor" name="json" rows="20" spellcheck="false" required>${e(pretty(payload))}</textarea></label><label class="file-input">Or load a JSON file<input type="file" accept=".json,application/json" id="dataset-file"></label><p class="fine-print">1–200 cases. Existing case IDs must remain stable for comparison. Saving creates a new version.</p>`,async data=>{const input=JSON.parse(data.get('json'));if(id)input.expectedVersion=original.version;await api(id?`/api/datasets/${id}`:'/api/datasets',{method:id?'PUT':'POST',body:input});toast('Dataset saved.');await load();},{wide:true,submit:'Save dataset version'});
  dialog.querySelector('#dataset-file').addEventListener('change',async event=>{try{dialog.querySelector('[name=json]').value=await readTextFile(event.target.files[0]);}catch(error){report(error);}});
}
function editPrompt(id){
  const original=state.prompts.find(p=>p.id===id)||{name:'Grounded assistant',system:'Use only the provided source context. State when information is missing.',template:'Context:\n{{context}}\n\nQuestion: {{question}}\nAnswer:'};
  modal(id?'Edit prompt · new revision':'Create prompt',`<label>Name<input name="name" maxlength="100" required value="${e(original.name)}"></label><label>System instruction<textarea name="system" rows="3" maxlength="4000">${e(original.system)}</textarea></label><label>Template<textarea class="json-editor" name="template" rows="9" maxlength="8000" required>${e(original.template)}</textarea></label><p class="fine-print">Use {{question}} and {{context}} only. Template contents are never executed.</p>`,async data=>{const body=Object.fromEntries(data);if(id)body.expectedVersion=original.version;await api(id?`/api/prompts/${id}`:'/api/prompts',{method:id?'PUT':'POST',body});toast('Prompt revision saved.');await load();},{wide:true,submit:'Save prompt revision'});
}

app.addEventListener('click',async event=>{
  const target=event.target.closest('button,[data-open-run],[data-case]');if(!target)return;
  try{
    if(target.dataset.tab){state.tab=target.dataset.tab;state.run=null;state.selectedCase=null;await load();return;}
    if(target.dataset.openRun){state.run=await api(`/api/runs/${target.dataset.openRun}`);state.tab='experiments';state.selectedCase=null;render();return;}
    if(target.dataset.case){state.selectedCase=target.dataset.case;render();return;}
    if(target.dataset.editDataset){await editDataset(target.dataset.editDataset);return;}
    if(target.dataset.editPrompt){editPrompt(target.dataset.editPrompt);return;}
    if(target.dataset.cancelJob){await api(`/api/jobs/${target.dataset.cancelJob}/cancel`,{method:'POST',body:{}});await load();return;}
    switch(target.dataset.action){
      case 'new-run':newRun();break;case 'new-dataset':await editDataset();break;case 'new-prompt':editPrompt();break;
      case 'connection':connectionDialog(load);break;case 'back-runs':state.run=null;state.selectedCase=null;render();break;
      case 'close-inspector':state.selectedCase=null;render();break;
      case 'export-csv':await download(`/api/runs/${state.run.id}/export.csv`,'tracepilot-results.csv');break;
      case 'export-json':await download(`/api/runs/${state.run.id}/export.json`,'tracepilot-run.json');break;
      case 'cancel-run':await api(`/api/jobs/${state.run.id}/cancel`,{method:'POST',body:{}});await load();break;
    }
  }catch(error){report(error);}
});
// The inspector is rendered beside the shell, so its actions are delegated from the document.
document.addEventListener('click',event=>{if(event.target.closest('[data-action="close-inspector"]')){state.selectedCase=null;render();}});
document.addEventListener('keydown',event=>{if(event.key==='Escape'&&state.selectedCase){state.selectedCase=null;render();}if(event.key==='Enter'&&event.target.matches('[data-open-run],[data-case]'))event.target.click();});
document.addEventListener('change',event=>{if(event.target.id==='result-filter'){state.filter=event.target.value;render();}});
document.addEventListener('submit',async event=>{
  if(!['annotation-form','compare-form'].includes(event.target.id))return;event.preventDefault();
  try{
    const data=new FormData(event.target);
    if(event.target.id==='annotation-form'){
      const row=state.run.results.find(r=>r.caseId===state.selectedCase);
      await api(`/api/runs/${state.run.id}/results/${row.caseId}/annotation`,{method:'PUT',body:{...Object.fromEntries(data),expectedRevision:row.revision}});toast('Annotation saved.');await load();
    }else{
      const comparison=await api('/api/compare',{method:'POST',body:Object.fromEntries(data)});
      document.querySelector('#comparison-output').innerHTML=`<div class="comparison-summary">${pill(`${comparison.pairedCases} paired cases`)}${pill(`${comparison.improvements} improvements`,'success')}${pill(`${comparison.regressions} regressions`,comparison.regressions?'danger':'neutral')}</div><div class="table-scroll"><table><thead><tr><th>CASE</th><th>BASELINE</th><th>CANDIDATE</th><th>F1 DELTA</th></tr></thead><tbody>${comparison.changes.map(c=>`<tr><td>${e(c.question)}</td><td>${pill(c.baselinePassed?'pass':'review',c.baselinePassed?'success':'danger')}</td><td>${pill(c.candidatePassed?'pass':'review',c.candidatePassed?'success':'danger')}</td><td class="mono">${c.f1Delta===null?'—':`${c.f1Delta>0?'+':''}${decimal(c.f1Delta)}`}</td></tr>`).join('')}</tbody></table></div>`;
    }
  }catch(error){report(error);}
});
load().catch(error=>{app.innerHTML=`<main class="boot-message"><h1>Connect to TracePilot</h1><p>${e(error.message)}</p><button class="button primary" id="reconnect">Workspace connection</button></main>`;document.querySelector('#reconnect').onclick=()=>connectionDialog(load);});
setInterval(async()=>{if(refreshing||document.querySelector('dialog[open]')||state.selectedCase||!state.jobs.some(j=>!terminal(j.status)))return;refreshing=true;try{await load();}catch(error){report(error);}finally{refreshing=false;}},1600);
