#version 100
precision mediump float;
uniform sampler2D uArt;
varying vec2 vUv;
varying vec3 vNormal;
void main() {
    vec4 art = texture2D(uArt, vUv);
    if (art.a < 0.01) discard;
    vec3 normal = normalize(vNormal);
    vec3 light = normalize(vec3(-0.5, 0.7, 1.2));
    float diffuse = max(dot(normal, light), 0.0);
    float shine = pow(max(dot(normal, normalize(light + vec3(0.0,0.0,1.0))),0.0),32.0);
    gl_FragColor = vec4(art.rgb * (0.68 + 0.32*diffuse) + vec3(0.08*shine*art.a), art.a);
}
