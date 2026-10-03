export type Rank = '15k'|'12k'|'10k'|'8k'|'5k'|'3k'|'1k'|'1d'|'3d'
export const RANKS: Rank[] = ['15k','12k','10k','8k','5k','3k','1k','1d','3d']
export type BoardPos = string // 9 lines of '.' 'X' 'O' or simple SGF moves list; we use 9x9 signMap flattened

