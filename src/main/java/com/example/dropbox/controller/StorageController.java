package com.example.dropbox.controller;

import com.example.dropbox.service.StorageService;
import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

import javax.websocket.server.PathParam;
import java.sql.*;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
@RestController
@CrossOrigin(origins = "*")

public class StorageController {
    private static final Gson gson = new Gson();
    @Autowired
    private StorageService service;

    @Value("${cloud.rds.credentials.url}")
    private String url;
    @Value("${cloud.rds.credentials.user_name}")
    private String user_name;

    @Value("${cloud.rds.credentials.password}")
    private String password;
    @Configuration
    public class MyConfiguration {

        @Bean
        public WebMvcConfigurer cors() {
            return new WebMvcConfigurerAdapter() {
                @Override
                public void addCorsMappings(CorsRegistry registry) {
                    registry.addMapping("/**");
                }
            };
        }
    }

    @GetMapping("/")
    public ResponseEntity<String> login(){
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @GetMapping("/download")
    public ResponseEntity<byte[]> download(@RequestParam("file") String file){
        return new ResponseEntity<>(service.downloadFile(file),HttpStatus.OK);

    }

    @GetMapping("/user_data")
    public ResponseEntity<String> get_data(@PathParam("username") String username, @PathParam("all") Boolean all){
        List<Object> big_jo = new ArrayList();
        String q = "";
        if(all){
            q = "select * from user";
        }
        else{
            q = "select * from user where email=?";

        }
        PreparedStatement pst = null;
        try (Connection con = DriverManager.getConnection(url, user_name, password)){
            if(all){
                pst = con.prepareStatement(q);
            }
            else {
                pst = con.prepareStatement(q);
                pst.setString(1, username);
                System.out.println("in pst false "+username+" "+q);
            }
            ResultSet rs = pst.executeQuery();
            while(rs.next()){
                Map<String, String> elements = new HashMap();
                elements.put("fileName", rs.getString("file_name"));
                elements.put("description", rs.getString("descrip"));
                elements.put("fileCreatedTime", rs.getString("upoad_time"));
                elements.put("fileUpdatedTime", rs.getString("upoaded_time"));
                elements.put("userName", rs.getString("email"));
                elements.put("id", rs.getString("id"));
                big_jo.add(elements);
            }
            System.out.println("in get user : "+big_jo);
        }catch (SQLException e) {
            return ResponseEntity.badRequest().body(gson.toJson("Bad request"));
        }
        return new ResponseEntity<>(gson.toJson(big_jo),HttpStatus.OK);
    }

    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(@RequestParam(value = "file") MultipartFile file,
                                             @RequestParam(value = "desc") String desc,
                                             @RequestParam(value = "email") String email
                                            ) {
        System.out.println("in upload"+ email);

        String just_name = file.getOriginalFilename();

//        LocalTime after_upload_time = LocalTime.now();

//        System.out.println("upload_time : "+before_upload_time+" "+after_upload_time+" "+email);
        //check if file exists and update the timestamp
        String q = "select * from user where email='"+email+"' and file_name like '%"+just_name+"'";
        try (Connection con = DriverManager.getConnection(url, user_name, password)){
             PreparedStatement pst = con.prepareStatement(q);
            ResultSet rs = pst.executeQuery();
            System.out.println("rs: "+rs);
            //update if exists
            if(rs.next()){
                System.out.println("exists");
                System.out.println(rs.getString("file_name"));
                String file_name = rs.getString("file_name");
                Timestamp before_upload_time= Timestamp.from(Instant.now());
                String file_name_from_service = service.uploadFile(file, file_name);
                Timestamp after_upload_time= Timestamp.from(Instant.now());
                pst = con.prepareStatement("update user set upoad_time=?, upoaded_time=?, descrip=? where file_name like '%"+just_name+"' and email = ?");
                pst.setString(1, before_upload_time.toString());
                pst.setString(2, after_upload_time.toString());
                pst.setString(3,desc);
                pst.setString(4,email);
                pst.executeUpdate();
                pst.close();
            }
            else{
                System.out.println("not exists");
                String file_name = System.currentTimeMillis() + "_" + file.getOriginalFilename();
                Timestamp before_upload_time= Timestamp.from(Instant.now());
                String file_name_from_service = service.uploadFile(file, file_name);
                Timestamp after_upload_time= Timestamp.from(Instant.now());
                String query = "insert into user(descrip, file_name, email, upoad_time, upoaded_time) values(?,?,?,?,?)";
                pst = con.prepareStatement(query);
                pst.setString(4, before_upload_time.toString());
                pst.setString(5, after_upload_time.toString());
                pst.setString(1, desc);
                pst.setString(2, file_name);
                pst.setString(3, email);
                pst.executeUpdate();
                pst.close();
                System.out.println("inserted");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
//        String query = "insert into user(descrip, file_name, email, upload_time, uploaded_time) values(?,?,?,?,?)";
//        try (Connection con = DriverManager.getConnection(url, user_name, password);
//             PreparedStatement pst = con.prepareStatement(query)) {
//                pst.setString(4, before_upload_time.toString());
//                pst.setString(5, after_upload_time.toString());
//                pst.setString(1, desc);
//                pst.setString(2, file_name);
//                pst.setString(3, email);
//                pst.executeUpdate();
//                pst.close();
//                con.close();
//        } catch (SQLException e) {
//            System.out.println(e);
//        }
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @DeleteMapping("/delete_files")
    public ResponseEntity<String> deleteFile(@RequestParam("id") String id, @RequestParam("file") String file) {
        System.out.println("in delete files,"+file);
        try (Connection con = DriverManager.getConnection(url, user_name, password);
             PreparedStatement pst = con.prepareStatement("delete from user where id = ?")) {
            pst.setString(1, id);
            pst.executeUpdate();
            pst.close();
            con.close();
        } catch (SQLException e) {
            System.out.println(e);
        }
        System.out.println("Done deleting files:"+file);
        return new ResponseEntity<>(service.deleteFile(file), HttpStatus.OK);
    }
}
