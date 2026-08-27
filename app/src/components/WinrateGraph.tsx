export function WinrateGraph({history}:{history:number[]}){
  if(history.length<2) return null
  const w=300,h=60, pad=4
  const min=Math.min(...history,0.4), max=Math.max(...history,0.6)
  const pts=history.map((v,i)=> `${(i/(history.length-1))* (w-pad*2)+pad},${h-pad - ((v-min)/(max-min||1))*(h-pad*2)}`).join(' ')
  return <svg width={w} height={h} style={{border:'1px solid #ddd', borderRadius:8, background:'#fff'}}>
    <polyline fill="none" stroke="#2c6" strokeWidth={2} points={pts}/>
    {history.map((v,i)=> <circle key={i} cx={(i/(history.length-1))* (w-pad*2)+pad} cy={h-pad - ((v-min)/(max-min||1))*(h-pad*2)} r={2} fill="#333"/>)}
  </svg>
}
