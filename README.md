# Napoleonic Line Infantry / Наполеоновская линейная пехота

Forge 1.20.1 addon for [Villager Recruits](https://www.curseforge.com/minecraft/mc-mods/recruits): shoulder arms, volley fire along the crosshair, fire at will, and a long musket reload. Optional client-only effects (smoke and tracers) live in a separate repository: [napoleonic-line-fx](https://github.com/sskjoldr/napoleonic-line-fx).

Forge 1.20.1 аддон для Villager Recruits: «на плечо», залп по точке прицела, свободный огонь и длинная перезарядка мушкета. Клиентские эффекты (дым и трассеры) вынесены в отдельный репозиторий: [napoleonic-line-fx](https://github.com/sskjoldr/napoleonic-line-fx).

## Requirements / Требования

- Minecraft 1.20.1, Forge 47.x (developed on 47.4.20), Java 17
- Villager Recruits 1.15.2 (required)
- Everything else is optional: the mod still starts when any of these is missing / всё остальное необязательно, мод запускается и без них:

| Mod | Version checked | Needed for |
| --- | --- | --- |
| Ewewukek's Musket Mod | 1.5.4 Forge | ewe muskets, pistols, blunderbuss |
| Recruits Use Boomsticks | 2.3.1 | recruit firearm AI for the mods below |
| Epic Knights: Thirty Years War | 1.3 Forge | matchlock musket and wheellock pistol |
| Epic Knights | 10.15 | required by Thirty Years War |
| Architectury API | 9.2.14 | required by Epic Knights |
| Cloth Config API | 11.1.136 | required by Epic Knights |
| Medieval Boomsticks | 1.01 | arquebus and handgonne (supported, not re-tested for 1.1.0) |

- `napoleonic_line_fx` is **client-only** and needs this main mod on both sides

### Tested with (Forge 1.20.1) / Проверено с

- Forge 1.20.1-47.4.20
- [Villager Recruits 1.15.2](https://www.curseforge.com/minecraft/mc-mods/recruits/files/8339846)
- [Ewewukek's Musket Mod 1.5.4](https://www.curseforge.com/minecraft/mc-mods/ewewukeks-musket-mod/files/5805565)
- [Recruits Use Boomsticks 2.3.1](https://www.curseforge.com/minecraft/mc-mods/recruits-use-boomsticks/files/9498108)
- [Epic Knights: Thirty Years War 1.3](https://www.curseforge.com/minecraft/mc-mods/epic-knights-thirty-years-war/files/9386507)
- [Epic Knights 10.15](https://www.curseforge.com/minecraft/mc-mods/epic-knights-armor-and-weapons/files/8931670)
- [Architectury API 9.2.14](https://www.curseforge.com/minecraft/mc-mods/architectury-api/files/5137938)
- [Cloth Config API 11.1.136](https://www.curseforge.com/minecraft/mc-mods/cloth-config/files/5729105)

## Play / Игра

Put `napoleonic_line-1.1.0.jar` in `mods` on the server and the client. Put the FX jar (`napoleonic_line_fx-1.1.0.jar`) on the client only if you want dense powder smoke, muzzle flash, and bullet tracers. Without it, shots use ordinary campfire smoke and no tracers. The FX jar is visual only and has no sounds.

Open the line menu with **N**. Orders go to the Recruits groups that are switched on.

## Epic Knights: Thirty Years War

With Recruits Use Boomsticks 2.3.1 and Thirty Years War installed, recruits armed with the matchlock musket or the wheellock pistol shoulder arms, fire volleys and fire at will, and use this mod's Napoleonic reload. Their ball is the line-infantry ball: about 35 damage, through netherite and Protection. A Thirty Years War gun keeps its own `knights.musket.shoot` sound, so the arquebus shot is not played on top of it. The integration is a soft dependency: its mixin is applied only when `thirtyyearswar` is loaded.

С Recruits Use Boomsticks 2.3.1 и Thirty Years War рекруты с фитильным мушкетом и колесцовым пистолетом берут оружие на плечо, стреляют залпом и свободным огнём и перезаряжаются по-наполеоновски; урон около 35, пробивает незерит. Мушкеты Thirty Years War стреляют своим звуком. Зависимость мягкая: без Thirty Years War мод запускается как обычно.

## Build / Сборка

```bash
./gradlew build
```

The jar is written to `build/libs/napoleonic_line-1.1.0.jar`.

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
