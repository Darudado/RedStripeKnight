package data.scripts.utils;

import com.fs.starfarer.api.Global;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

public class GL_tools {
    public static boolean ifBoxUnitLoad(){
       return Global.getSettings().getModManager().isModEnabled("BoxUtil");
    }
    public static int createShader(String source,int shaderType){
        if(!GLContext.getCapabilities().OpenGL20){
            Global.getLogger(Global.class).info("error1_1");
            return 0;
        }
        int shaderID = GL20.glCreateShader(shaderType);
        GL20.glShaderSource(shaderID,source);
        GL20.glCompileShader(shaderID);
        if(GL20.glGetShaderi(shaderID,GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE){
            Global.getLogger(Global.class).info("error1_2 : " + GL20.glGetShaderInfoLog(shaderID,GL20.glGetShaderi(shaderID,GL20.GL_INFO_LOG_LENGTH)));
            GL20.glDeleteShader(shaderID);
            return 0;
        }else {
            Global.getLogger(Global.class).info("error1_3");
            return shaderID;
        }
    }
    public static int createShaderProgram(String vertSource,String fragSource){
        if(!GLContext.getCapabilities().OpenGL20){
            Global.getLogger(Global.class).info("error2_1");
            return 0;
        }
        int programID = GL20.glCreateProgram();
        int[] shaders = new int[]{createShader(vertSource,GL20.GL_VERTEX_SHADER),createShader(fragSource,GL20.GL_FRAGMENT_SHADER)};
        if(shaders[0] == 0 || shaders[1] == 0){
            return 0;
        }
        GL20.glAttachShader(programID,shaders[0]);
        GL20.glAttachShader(programID,shaders[1]);
        GL20.glLinkProgram(programID);
        if(GL20.glGetShaderi(programID,GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE){
            Global.getLogger(Global.class).info("error2_2");
            GL20.glDeleteProgram(programID);
            GL20.glDetachShader(programID,shaders[0]);
            GL20.glDeleteShader(shaders[0]);
            GL20.glDetachShader(programID,shaders[1]);
            GL20.glDeleteShader(shaders[1]);
            return 0;
        }else {
            Global.getLogger(Global.class).info("error2_3");
            return programID;
        }
    }





}
