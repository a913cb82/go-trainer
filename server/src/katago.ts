import { spawn, ChildProcess } from 'node:child_process'
import { existsSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))
const SERVER_ROOT = resolve(__dirname, '..')
const defaultModel = resolve(SERVER_ROOT, 'models', 'strong.bin.gz')
const defaultHumanModel = resolve(SERVER_ROOT, 'models', 'b18c384nbt-humanv0.bin.gz')
const defaultConfig = resolve(SERVER_ROOT, 'config', 'analysis.cfg')

export type KatagoMode = 'real'
type Pending = { resolve:(v:any)=>void, reject:(e:any)=>void }

export class KatagoEngine {
  mode: KatagoMode = 'real'
  proc?: ChildProcess
  pending = new Map<string, Pending>()
  buf = ''

  constructor(){
    const bin = process.env.KATAGO_BINARY || process.env.KATAGO_BIN || './katago'
    if(!existsSync(bin)) throw new Error(`KATAGO_MODE=real but binary not found at ${bin} — run ./server/scripts/download-models.sh`)
    if(!existsSync(defaultModel)) throw new Error(`KATAGO_MODE=real but model not found at ${defaultModel}`)
    if(!existsSync(defaultHumanModel)) throw new Error(`KATAGO_MODE=real but human model not found at ${defaultHumanModel}`)
    if(!existsSync(defaultConfig)) throw new Error(`KATAGO_MODE=real but config not found at ${defaultConfig}`)
    this.spawn(bin)
  }
  spawn(bin:string){
    const model = process.env.KATAGO_MODEL || defaultModel
    const humanModel = process.env.KATAGO_HUMAN_MODEL || defaultHumanModel
    const config = process.env.KATAGO_CONFIG || defaultConfig
    const args = ['analysis','-model',model,'-human-model',humanModel,'-config',config]
    const ld = [
      process.env.LD_LIBRARY_PATH || '',
      '/usr/local/cuda/targets/x86_64-linux/lib',
      resolve(SERVER_ROOT, 'libs'),
      SERVER_ROOT,
      resolve(SERVER_ROOT, '..', 'libs')
    ].filter(Boolean).join(':')
    this.proc = spawn(bin, args, {stdio:['pipe','pipe','pipe'], cwd: process.cwd(), env: {...process.env, LD_LIBRARY_PATH: ld}})
    this.proc.stdout?.on('data',d=> this.onData(d.toString()))
    this.proc.stderr?.on('data',d=> console.error('[katago]', d.toString().slice(0,500)))
    this.proc.on('error',(err)=> { console.error('katago spawn failed', err); throw err })
  }
  onData(chunk:string){
    this.buf+=chunk
    let idx
    while((idx=this.buf.indexOf('\n'))>=0){
      const line=this.buf.slice(0,idx).trim(); this.buf=this.buf.slice(idx+1)
      if(!line) continue
      try{ const obj=JSON.parse(line); const p=this.pending.get(obj.id); if(p){ this.pending.delete(obj.id); p.resolve(obj)} }catch{}
    }
  }
  async query(obj:any, timeoutMs=3000): Promise<any>{
    if(obj.rank && !obj.overrideSettings?.humanSLProfile){
      const profile = 'rank_' + String(obj.rank)
      obj.overrideSettings = {...(obj.overrideSettings||{}), humanSLProfile: profile}
    }
    return new Promise((resolve,reject)=>{
      const id = obj.id || Math.random().toString(36).slice(2)
      obj.id=id
      this.pending.set(id,{resolve,reject})
      this.proc!.stdin!.write(JSON.stringify(obj)+'\n')
      setTimeout(()=>{ if(this.pending.has(id)){ this.pending.delete(id); reject(new Error('katago timeout')) } }, timeoutMs)
    })
  }
}
