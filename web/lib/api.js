const KEY_NAME = 'tracepilot.workspace-key';
export class ApiError extends Error {
  constructor(message, status, requestId) {super(message);this.status=status;this.requestId=requestId;}
}
export function workspaceKey() { return sessionStorage.getItem(KEY_NAME) || ''; }
export function setWorkspaceKey(key) {
  if (key) sessionStorage.setItem(KEY_NAME, key); else sessionStorage.removeItem(KEY_NAME);
}
export async function api(path, options = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), options.timeout || 110000);
  try {
    const response = await fetch(path, {
      method: options.method || 'GET', signal: controller.signal,
      headers: {'Content-Type':'application/json', 'X-Workspace-Key':workspaceKey(), ...(options.idempotency ? {'Idempotency-Key':options.idempotency} : {})},
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    });
    if (!response.ok) {
      let problem;try {problem=await response.json();} catch {problem={detail:`Request failed (${response.status})`};}
      throw new ApiError(problem.detail || problem.title, response.status, problem.requestId);
    }
    return options.raw ? response : response.json();
  } finally {clearTimeout(timer);}
}
export async function download(path, filename) {
  const response = await api(path, {raw:true});
  const url = URL.createObjectURL(await response.blob());
  const link = document.createElement('a');link.href=url;link.download=filename;link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
export async function waitForJob(id, onProgress = () => {}, maxMillis = 240000) {
  const started=Date.now();
  while (Date.now()-started < maxMillis) {
    const job=await api(`/api/jobs/${encodeURIComponent(id)}`);onProgress(job);
    if (['FAILED','CANCELLED'].includes(job.status)) throw new Error(job.error || `Job ${job.status.toLowerCase()}`);
    if (job.status==='SUCCEEDED') return job;
    await new Promise(resolve => setTimeout(resolve, 400));
  }
  throw new Error('Still running. Open Activity to check the job; it has not been cancelled.');
}
