using System;
using System.Collections;
using System.Collections.Generic;
using System.IO;
using UnityEngine;
using UnityEngine.Rendering;

public class EncAll : MonoBehaviour
{
    FileInfo theSourceFile = null;
    StreamReader reader = null;
    string text = " ";
    public string histFilename = "./histTrace.txt";
    public bool encFlag = false;
    public string frameDirName = "./frame/";

    RenderTexture equirect;
    RenderTexture cubemap;
    FileStream fs;
    int sqr = 1024;
    public int width = 1920;
    public int height = 1080;
    public new Camera camera;
    string pose;
    Vector3 init_pos;
    Vector3 prac_pos;
    public float granular = 0.05f; 

    int enc_width;
    int enc_height;

    long last_frame_time = 0;
    int cnt = 0;

    // Start is called before the first frame update
    void Start()
    {
        //text = reader.ReadLine();
        if (encFlag)
        {
            cubemap = new RenderTexture(sqr, sqr, 24, RenderTextureFormat.BGRA32);
            cubemap.dimension = TextureDimension.Cube;
            equirect = new RenderTexture(width, height, 24, RenderTextureFormat.BGRA32);
            enc_width = width;
            enc_height = height;
        }
        init_pos = transform.position;
        prac_pos = transform.position;

        //File.ReadLines(histFilename).Last();
        //Debug.Log(histFilename);
    }

    // Update is called once per frame
    void Update()
    {
        Vector3 pos = transform.position;
        if ((pos.z - init_pos.z) >= 30) return;
        
        prac_pos.x = pos.x - init_pos.x;
        prac_pos.z = pos.z - init_pos.z;
        Decimal posX = Math.Round((Decimal)(prac_pos.x * 100.0));
        Decimal posZ = Math.Round((Decimal)((prac_pos.z) * 100.0));
        pose = "(" + (int)(posX) + "," + (int)(posZ) + ")";
        cnt++;
        Debug.Log(cnt + " " + pos + " " + pose );

        StreamWriter file = new StreamWriter(histFilename, true);
        file.WriteLine(cnt + " " + pos + " " + pose);
        file.Close();

        //transform.eulerAngles = ori;
        //text = reader.ReadLine();
    }

    void LateUpdate()
    {
        Vector3 pos = transform.position;
        if ((pos.z - init_pos.z) >= 30) return;

        geneFrame();

        if ((pos.x + granular - init_pos.x) < 26)
        {
            pos.x += granular;
        }
        else
        {
            pos.z += granular;
            pos.x = init_pos.x;
        }

        transform.position = pos;
    }

    void geneFrame()
    {
        string pos_file_name = frameDirName + pose + ".png";
        // encode to png 
        camera.RenderToCubemap(cubemap, 63, Camera.MonoOrStereoscopicEye.Mono);
        cubemap.ConvertToEquirect(equirect, Camera.MonoOrStereoscopicEye.Mono);
        Texture2D ori_tex = new Texture2D(width , height, TextureFormat.BGRA32, false);
        ori_tex.ReadPixels(new Rect(0 , 0, ori_tex.width, ori_tex.height), 0, 0, false);
        ori_tex.Apply();
        byte[] tex_bytes = ori_tex.EncodeToPNG();
        System.IO.File.WriteAllBytes(frameDirName + pose + ".png", tex_bytes);
        Destroy(ori_tex);
        tex_bytes = null;
    }

    private void OnDestroy()
    {
        if (reader != null)
            reader.Close();
    }
}