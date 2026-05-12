# TruthfulAC

> Movement-first. Open source.

TruthfulAC is a Minecraft anti-cheat focused on simulation-based movement detection. It's not trying to be the best anti-cheat ever made, it's trying to be a solid, honest one that actually works and that the community can build on together.

Grim handles movement really well. What it doesn't do is combat. TruthfulAC fills that gap with GCD analysis, basic aim consistency checks, and a movement suite built around simulation rather than guesswork. Nothing overcomplicated. Nothing that hands cheaters a free roadmap either.

This used to be a paid plugin. It's not anymore. The license key system is gone, the obfuscation is gone, and now it's here for anyone who wants to use it, learn from it, or help make it better.

---

## What it checks

**Movement / Simulation** - The core of the plugin. Simulation-based checks that track how players move and flag what doesn't add up. This is where the most work has gone and where most future work will go.

**Reach & Hitbox** - Standard but solid. Does what it says.

**Combat (basic)** - GCD detection and basic aim consistency. Not trying to reinvent the wheel, just catching the obvious stuff that other open source options miss.

---

## What it doesn't do

It's not going to catch everything. No anti-cheat does. The goal is to keep servers reasonably clean and give the community something maintainable and understandable, not a black box you just hope works.

---

## Contributing

Contributions are welcome but there are rules.

Movement checks are the priority. If you're submitting something, movement-related PRs will get the most attention and are the most likely to get merged. Combat and other checks are accepted too but they need to actually be good, not a half-baked implementation pulled from somewhere else.

**What we don't want:**
- Overcomplicated combat checks that create more false positives than actual catches
- Packet or world-related checks that are out of scope
- Anything where you clearly don't understand what you're submitting
- Low-effort ports of existing public check implementations

**What we do want:**
- Solid movement check improvements or new simulation-based detections
- Bug fixes with a clear explanation of what was wrong and why the fix works
- Performance improvements
- Anything that makes the codebase cleaner or easier to follow

If you hit 30 or more meaningful contributions you'll get a spot in the in-game credit scene inside the plugin GUI. That's the only credit system. Earn it in the code.

Open a PR, explain what you did and why, and keep it clean.

---

## License

Licensed under the **Truthful & Faithful License v1.0**, see the [LICENSE](LICENSE.md) file for full terms.

Short version: free to use, study, modify, and contribute. You cannot sell it, monetize it, or redistribute it under a different license.

---

## Links

- [Discord](https://discord.gg/DA88PBbseD)
- [Modrinth](https://modrinth.com/project/truthfulac)
- 
