# Napoleonic Line Infantry / Наполеоновская линейная пехота

Forge 1.20.1 addon for [Villager Recruits](https://www.curseforge.com/minecraft/mc-mods/recruits): shoulder arms, volley fire along the crosshair, fire at will, and a long musket reload. Optional client-only effects (smoke and tracers) live in a separate repository: [napoleonic-line-fx](https://github.com/sskjoldr/napoleonic-line-fx).

Forge 1.20.1 аддон для Villager Recruits: «на плечо», залп по точке прицела, свободный огонь и длинная перезарядка мушкета. Клиентские эффекты (дым и трассеры) вынесены в отдельный репозиторий: [napoleonic-line-fx](https://github.com/sskjoldr/napoleonic-line-fx).

## Requirements / Требования

- Minecraft 1.20.1, Forge 47.x (developed on 47.4.20), Java 17
- Villager Recruits 1.15.2 (required)
- ewewukek's Musket Mod 1.5.4 (optional; the mod still starts without it)
- `napoleonic_line_fx` is **client-only** and needs this main mod on both sides

## Play / Игра

Put `napoleonic_line-1.0.0.jar` in `mods` on the server and the client. Put the FX jar on the client only if you want dense powder smoke and bullet tracers. Without it, shots use ordinary campfire smoke and no tracers.

Open the line menu with **N**. Orders go to the Recruits groups that are switched on.

## Build / Сборка

```bash
./gradlew build
```

The jar is written to `build/libs/napoleonic_line-1.0.0.jar`.

## Server configs / Конфиги сервера

Recommended copies (already adjusted for this pack) are under `server-pack/`:

- `server-pack/config/musketmod.txt` → world `config/musketmod.txt`
- `server-pack/config/napoleonic_line-client.toml` → `config/napoleonic_line-client.toml`
- `server-pack/defaultconfigs/napoleonic_line-server.toml` → `defaultconfigs/` (or `world/serverconfig/`)
- `server-pack/world/serverconfig/recruits-server.toml` → `world/serverconfig/recruits-server.toml`

## Arquebus sound / Звук аркебуза

The arquebus report `musket_fire_4` is bundled in this main mod (`assets/napoleonic_line/sounds/musket_fire_4.ogg`), so arquebuses sound the same with or without the visual effects mod [napoleonic-line-fx](https://github.com/sskjoldr/napoleonic-line-fx).

Звук выстрела аркебузы вшит в основной мод, поэтому аркебузы звучат одинаково и без мода визуальных эффектов.

Sample: [musket_fire_4.wav (#648747) by J8054](https://freesound.org/people/J8054/sounds/648747/), [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).

## License

MIT. Sound sample above is CC BY 4.0 (J8054 / Freesound).
