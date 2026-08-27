export type Rank = '15k'|'12k'|'10k'|'8k'|'5k'|'3k'|'1k'|'1d'|'3d'
export const RANKS: Rank[] = ['15k','12k','10k','8k','5k','3k','1k','1d','3d']
export type Strategy = 'good-vs-tempting'|'human-only'|'tesuji'|'blunder-check'|'strong-only'
export type Candidate = { x:number,y:number,label:string,humanPolicy:number,strongWinrate:number,strongScore:number,tag?:string }
export type BoardPos = string // 9 lines of '.' 'X' 'O' or simple SGF moves list; we use 9x9 signMap flattened

export function rankIndex(r:Rank){ return RANKS.indexOf(r) }
