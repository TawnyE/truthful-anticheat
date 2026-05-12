# TruthfulAC

TruthfulAC is a Minecraft anti-cheat built around simulation-based movement detection and a full combat suite. It is not trying to be the best anti-cheat ever made. It is trying to be a solid, honest one that actually works and that the community can build on together.

This used to be a paid plugin. It is not anymore. The license key is gone, the obfuscation is gone, and the full source is here for anyone who wants to use it, learn from it, or help make it better.

---

## What it checks

**Movement** — The core of the plugin. A full server-side physics engine that validates movement every tick against what vanilla actually allows. Covers fly, bhop, speed, elytra abuse, liquid movement, vehicle exploits, NoFall, velocity, and timer manipulation. This is where the most work has gone and where most future work will go.

**Combat** — A complete combat detection suite. Eleven aim checks covering GCD analysis, jerk detection, entropy, Bayesian classification, target lock, and more. Seven KillAura checks. Five AutoClicker checks. Reach, hitbox, raycast, crystal aura, and anchor aura. Not basic.

**World and Packets** — Scaffold, fastbreak, phase, bad packet detection, packet order violations, sprint abuse, and crash exploit protection.

**Bedrock** — Dedicated checks for Geyser-translated Bedrock players with separate thresholds. Bedrock players never run Java physics checks.

---

## What it doesn't do

It will not catch everything. No anti-cheat does. The goal is to keep servers reasonably clean and give the community something maintainable and transparent, not a black box you just hope works.

---

## Contributing

Contributions are welcome but there are standards.

Movement checks are the priority. Combat and other checks are accepted too but they need to actually be good, not a half-baked port of something else.

**What is not wanted:**
- Checks that produce more false positives than actual catches
- Low effort ports of existing public implementations
- PRs with no explanation of what changed or why
- Anything where it is clear the submitter does not understand what they are submitting

**What is wanted:**
- Movement check improvements and new simulation-based detections
- Bug fixes with a clear explanation of what was wrong and why the fix works
- Performance improvements
- Anything that makes the codebase cleaner or easier to follow

Hit 30 or more meaningful contributions and you get a named credit in the in-game plugin GUI. That is the only credit system. Earn it in the code.

Open a PR, explain what you did and why, and keep it clean.

---

## License

Licensed under the **Truthful and Faithful License v1.1**, see the [LICENSE](LICENSE.md) file for full terms.

Free to use, study, modify, and contribute. You cannot sell the plugin or redistribute it under a different license. Custom configuration files and presets are exempt and can be sold freely as long as the plugin jar is not bundled with them.

---

## Links

- [Discord](https://discord.gg/DA88PBbseD)
- [Modrinth](https://modrinth.com/project/truthfulac)
