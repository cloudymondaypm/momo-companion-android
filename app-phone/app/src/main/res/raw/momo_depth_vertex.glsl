#version 100
attribute vec3 aPosition;
attribute vec3 aNormal;
attribute vec2 aUv;
uniform vec2 uFit;
uniform float uAngle;
varying vec2 vUv;
varying vec3 vNormal;
void main() {
    float c = cos(uAngle);
    float s = sin(uAngle);
    mat3 turn = mat3(c,0.0,-s, 0.0,1.0,0.0, s,0.0,c);
    vec3 position = turn * aPosition;
    gl_Position = vec4(position.xy * uFit, -position.z * 0.3, 1.0);
    vNormal = turn * aNormal;
    vUv = aUv;
}
