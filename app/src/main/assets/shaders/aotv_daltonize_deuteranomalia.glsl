// AOTV — daltonizacao (deuteranomalia), Lote mpv 2 (v1.37.0).
// Correcao (daltonizacao), NAO simulacao: a matriz 3x3 abaixo e a mesma de
// ColorFilter.kt (DALTONIZATION_RGB, algoritmo LMS do daltonize.org —
// http://www.daltonize.org/2010/05/lms-daltonization-algorithm.html — via o
// port p/ ReShade). O mpv nao tem matriz de cor propria: a correcao vira
// shader GLSL e entra no glsl-shaders junto com a cadeia do upscaler (ou
// sozinho, quando o upscaler esta desligado). Codigo proprio do AOTV (MIT).
//!HOOK MAIN
//!BIND HOOKED
//!DESC AOTV daltonizacao deuteranomalia
vec4 hook() {
    vec3 c = HOOKED_tex(HOOKED_pos).rgb;
    vec3 o;
    o.r = 1.000000 * c.r + 0.000000 * c.g + 0.000000 * c.b;
    o.g = 0.202325 * c.r + 0.797674 * c.g + 0.000000 * c.b;
    o.b = 0.517411 * c.r + -0.517413 * c.g + 1.000000 * c.b;
    return vec4(o, 1.0);
}
