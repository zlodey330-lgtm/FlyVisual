# FlyVisual

Визуальный клиентский мод для Minecraft **1.20.1** (Fabric). Красная тема по умолчанию, цвет меняется в меню.

## Что нового в 0.4

- **TargetHUD:** здоровье, броня и расстояние до того, на кого смотришь (настройки: время показа, только игроки, броня). В HUD-редакторе показывается образец.
- **Hitboxes:** свои хитбоксы с выбором цвета (Color hue, Brightness, Opacity, Range, Players only). Рисуются за блоками, насквозь не видны.
- **Hitcolor:** свой цвет красной вспышки, когда по существу бьют (Color hue, Strength).
- **Fog:** убрать туман совсем (Remove fog) или сделать ближе/дальше (Distance). Под водой, в лаве, в Незере и при слепоте не работает.

Пока не сделано: прозрачность воды и лавы, цвет блеска зачарований (Glint Color). Это лезет в глубокие части рендера, отдельным шагом.

## Что нового в 0.3: настройки у каждой функции

ПКМ по модулю в меню открывает настройки. Там бывают ползунки и переключатели Вкл/Выкл.

- **Все HUD-модули:** Opacity (прозрачность фона), Scale (размер), Accent bar (цветная полоска)
- **Zoom:** Zoom level, Max zoom, Scroll speed, Smooth zoom, Smoothness
- **Fullbright:** Brightness
- **FreeLook:** Sensitivity, Third person
- **Lower Fire:** Offset
- **Hand Offset:** X, Y, Z, Scale
- **Coordinates:** Decimals (знаков после запятой)
- **Armor HUD:** Show durability %, Warn below %
- **Potion Status:** Show timer
- **Watermark:** Show nickname
- **NoHurtCam:** только вкл/выкл, как ты просил

## Что есть в версии 0.2

**Меню (Right Shift)**
- ЛКМ по модулю: включить/выключить
- ПКМ по модулю (или стрелка `>`): настройки с ползунками
- Колесо мыши: прокрутка списка
- Внизу: цвет темы и кнопка **HUD Editor** (перетаскивай модули мышкой, ESC для возврата)

**HUD:** Watermark, FPS, Ping, Coordinates, Keybinds (WASD/мышь/пробел), Armor HUD, Potion Status

**Камера и рука**
- **Fullbright**
- **NoHurtCam**: без тряски камеры при уроне
- **Lower Fire**: огонь на экране ниже (ползунок Offset)
- **Zoom**: держи `C`, колёсико меняет приближение, плавный вход и выход
- **FreeLook**: держи `Left Alt`, обзор на 360°, тело не поворачивается
- **Hand Offset**: сдвиг по X/Y/Z и размер предмета в руке

Клавиши можно поменять в настройках управления игры (категория FlyVisual).
Конфиг: `config/flyvisual.json`.

## Сборка через GitHub (без установки Java)

После каждого `git push` GitHub сам собирает мод: вкладка **Actions** → последний запуск → внизу **Artifacts** → `FlyVisual` (внутри jar).

## Сборка на своём компьютере

Нужно: **JDK 17** и **IntelliJ IDEA**. Открой папку проекта, дождись Gradle, затем `./gradlew runClient` (тест) или `./gradlew build` (jar в `build/libs`).

Чтобы играть, положи jar и **Fabric API 0.92.2 для 1.20.1** в папку `mods`.

## Как добавить новую функцию

1. Создай класс в `com.flyvisual.module.impl`, наследуй `Module` (или `HudModule`, если рисует на экране).
2. Настройки-ползунки: `addSetting("Имя", по умолчанию, мин, макс, шаг)`.
3. Добавь одну строку в `ModuleManager.init()`.

## Если что-то не работает

Миксины (Zoom, FreeLook, Lower Fire, Hand Offset, NoHurtCam) целятся в внутренние методы игры, и я не мог проверить их в запущенной игре. Если какая-то функция не работает, найди в `logs/latest.log` строки со словом `flyvisual` или `Mixin` и пришли их. Если игра вылетела при запуске, пришли файл из `crash-reports`.
