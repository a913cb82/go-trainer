# Go Game Project

## Overview
This project implements a Go (also known as Baduk or Weiqi) game engine with a playable interface.

## Features
- [ ] Game board representation (19x19 standard, with support for other sizes)
- [ ] Stone placement and capture rules
- [ ] Ko rule prevention
- [ ] Territory scoring
- [ ] Pass and resign functionality
- [ ] GTP (Go Text Protocol) support
- [ ] Basic AI opponent
- [ ] GUI interface
- [ ] Game saving/loading
- [ ] SGF (Smart Game Format) support

## Project Structure
```
go_game/
├── src/                 # Source code
│   ├── board/           # Board logic and representation
│   ├── rules/           # Game rules enforcement
│   ├── players/         # Human and AI players
│   ├── interface/       # CLI, GUI, and GTP interfaces
│   └── utils/           # Utility functions
├── tests/               # Test suite
├── docs/                # Documentation
├── README.md            # This file
└── AGENTS.md -> README.md  # Symlink to README
```

## Development Plan
1. **Phase 1: Core Engine**
   - Implement board data structure
   - Implement legal move checking
   - Implement capture mechanics
   - Implement ko rule

2. **Phase 2: Game Flow**
   - Turn management
   - Scoring system
   - Game end detection

3. **Phase 3: Interfaces**
   - Command-line interface
   - GTP engine for external programs
   - Basic GUI

4. **Phase 4: AI Opponent**
   - Random player
   - Simple heuristic AI
   - Monte Carlo Tree Search (optional)

5. **Phase 5: Polish**
   - Game saving/loading
   - SGF support
   - Comprehensive testing

## Technologies
- Language: To be determined (considering Python, Go, or Rust)
- GUI: TBD
- Testing: Unit tests for core logic

## Contributing
Feel free to submit issues and pull requests.

## License
MIT