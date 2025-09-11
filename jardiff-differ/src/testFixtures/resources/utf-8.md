> [!IMPORTANT]
> This file is supposed to be **UTF-8**

> Café naïve jalapeño

UTF-8 encodes é as two bytes (`0xC3` `0xA9`), but in Latin-1 those bytes show as “Ã©”.

> The temperature is 19°C 🌤️

* The degree sign (°) is `0xC2` `0xBA` in UTF-8.
* The sun emoji (🌤️) is three code points. Wrong decoders may show gibberish or replacement boxes.

> Привет мир (Hello world in Russian)
> こんにちは世界 (Hello world in Japanese)
> مرحبا بالعالم (Hello world in Arabic)

These characters require 2–3 bytes each in UTF-8. Single-byte decoders will definitely break.

> Café (the e + combining acute U+0301)

This looks identical to “Café” but is actually two code points.