package com.todo.taskManager.service.impl;

import com.todo.taskManager.constant.FileConstant;
import com.todo.taskManager.constant.UserImplConstant;
import com.todo.taskManager.domain.User;
import com.todo.taskManager.domain.UserPrincipal;
import com.todo.taskManager.enumeration.Role;
import com.todo.taskManager.exception.domain.EmailExistException;
import com.todo.taskManager.exception.domain.EmailNotFoundException;
import com.todo.taskManager.exception.domain.NotAnImageFileException;
import com.todo.taskManager.exception.domain.UsernameExistException;
import com.todo.taskManager.repository.UserRepository;
import com.todo.taskManager.service.EmailService;
import com.todo.taskManager.service.LoginAttemptService;
import com.todo.taskManager.service.UserService;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import javax.mail.MessagingException;
import javax.transaction.Transactional;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Service
@Transactional
@Qualifier("userDetailsService")
public class UserServiceImpl implements UserService, UserDetailsService {

  private Logger LOGGER = org.slf4j.LoggerFactory.getLogger(getClass());
  private UserRepository userRepository;
  private BCryptPasswordEncoder passwordEncoder;
  private LoginAttemptService loginAttemptService;
  private EmailService emailService;

  @Autowired
  public UserServiceImpl(
    UserRepository userRepository,
    BCryptPasswordEncoder passwordEncoder,
    LoginAttemptService loginAttemptService,
    EmailService emailService
  ) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.loginAttemptService = loginAttemptService;
    this.emailService = emailService;
  }

  @Override
  public UserDetails loadUserByUsername(String username)
    throws UsernameNotFoundException {
    User user = userRepository.findUserByUsername(username);

    if (user == null) {
      LOGGER.error("User not found by username: " + username);
      throw new UsernameNotFoundException(
        UserImplConstant.NO_USER_FOUND_BY_USERNAME + username
      );
    } else {
      validateLoginAttempt(user);
      user.setLastLoginDateDisplay(user.getLastLoginDate());
      user.setLastLoginDate(new Date());
      userRepository.save(user);
      UserPrincipal userPrincipal = new UserPrincipal(user);
      LOGGER.info(UserImplConstant.FOUND_USER_BY_USERNAME + username);
      return userPrincipal;
    }
  }

  private void validateLoginAttempt(User user) {
    if (user.isNotLocked()) {
      if (loginAttemptService.hasExceededMaxAttempts(user.getUsername())) {
        user.setNotLocked(false);
      } else {
        user.setNotLocked(true);
      }
    } else {
      loginAttemptService.evictUserFromLoginAttempCache(user.getUsername());
    }
  }

  @Override
  public User register(
    String firstName,
    String lastName,
    String username,
    String email
  )
    throws UsernameNotFoundException, UsernameExistException, EmailExistException, MessagingException {
    validateNewUsernameAndEmail(StringUtils.EMPTY, username, email);
    User user = new User();
    user.setUserId(generateUserId());
    String password = generatePassword();
    String encordedPassword = encordedPassword(password);
    user.setFirstName(firstName);
    user.setLastName(lastName);
    user.setUsername(username);
    user.setEmail(email);
    user.setJoinDate(new Date());
    user.setPassword(encordedPassword);
    user.setActive(true);
    user.setNotLocked(true);
    user.setRole(Role.ROLE_USER.name());
    user.setAuthorities(Role.ROLE_USER.getAuthorities());
    user.setProfileImageUrl(getTemporaryProfileImageUrl(username));

    userRepository.save(user);
    LOGGER.info("New user password " + password);
    emailService.sendNewPasswordEmail(firstName, password, email);

    return user;
  }

  private String getTemporaryProfileImageUrl(String username) {
    return ServletUriComponentsBuilder
      .fromCurrentContextPath()
      .path(FileConstant.DEFAULT_USER_IMAGE_PATH + username)
      .toUriString();
  }

  private String encordedPassword(String password) {
    return passwordEncoder.encode(password);
  }

  private String generatePassword() {
    return RandomStringUtils.randomAlphanumeric(10);
  }

  private String generateUserId() {
    return RandomStringUtils.randomNumeric(10);
  }

  private User validateNewUsernameAndEmail(
    String currentUsername,
    String newUsername,
    String newEmail
  )
    throws UsernameNotFoundException, UsernameExistException, EmailExistException {
    User userByNewUsername = findUserByUsername(newUsername);
    User userByNewEmail = findUserByEmail(newEmail);

    if (StringUtils.isNotBlank(currentUsername)) {
      User currentUser = findUserByUsername(currentUsername);
      if (currentUser == null) {
        throw new UsernameNotFoundException(
          UserImplConstant.NO_USER_FOUND_BY_USERNAME + currentUsername
        );
      }

      if (
        userByNewUsername != null &&
        !currentUser.getId().equals(userByNewUsername.getId())
      ) {
        throw new UsernameExistException(
          UserImplConstant.USERNAME_ALREADY_EXISTS
        );
      }

      if (
        userByNewEmail != null &&
        !currentUser.getId().equals(userByNewEmail.getId())
      ) {
        throw new EmailExistException(UserImplConstant.EMAIL_ALREADY_EXISTS);
      }

      return currentUser;
    } else {
      if (userByNewUsername != null) {
        throw new UsernameExistException(
          UserImplConstant.USERNAME_ALREADY_EXISTS
        );
      }

      if (userByNewEmail != null) {
        throw new EmailExistException(UserImplConstant.EMAIL_ALREADY_EXISTS);
      }

      return null;
    }
  }

  @Override
  public List<User> getUsers() {
    return userRepository.findAll();
  }

  @Override
  public User findUserByUsername(String username) {
    return userRepository.findUserByUsername(username);
  }

  @Override
  public User findUserByEmail(String email) {
    return userRepository.findUserByEmail(email);
  }

  @Override
  public User addNewUser(
    String firstName,
    String lastName,
    String username,
    String email,
    String role,
    boolean isNotLocked,
    boolean isActive,
    MultipartFile profileImage
  )
    throws UsernameNotFoundException, UsernameExistException, EmailExistException, IOException, NotAnImageFileException {
    validateNewUsernameAndEmail(StringUtils.EMPTY, username, email);

    User user = new User();
    String password = generatePassword();

    System.out.println("ADD USER PASSWORD " + password);

    String encodePassword = encordedPassword(password);
    user.setUserId(generateUserId());
    user.setFirstName(firstName);
    user.setLastName(lastName);
    user.setJoinDate(new Date());
    user.setUsername(username);
    user.setEmail(email);
    user.setPassword(encodePassword);
    user.setActive(isActive);
    user.setNotLocked(isNotLocked);
    user.setRole(getRoleEnumName(role).name());
    user.setAuthorities(getRoleEnumName(role).getAuthorities());
    user.setProfileImageUrl(getTemporaryProfileImageUrl(username));
    userRepository.save(user);
    saveProfileImage(user, profileImage);

    return user;
  }

  private void saveProfileImage(User user, MultipartFile profileImage)
    throws IOException, NotAnImageFileException {
    if (profileImage != null) {
      if (
        !Arrays
          .asList(
            MimeTypeUtils.IMAGE_JPEG_VALUE,
            MimeTypeUtils.IMAGE_PNG_VALUE,
            MimeTypeUtils.IMAGE_GIF_VALUE
          )
          .contains(profileImage.getContentType())
      ) {
        throw new NotAnImageFileException(
          profileImage.getOriginalFilename() +
          "is not an image file. Please upload an image"
        );
      }

      Path userFolder = Paths
        .get(FileConstant.USER_FOLDER + user.getUsername())
        .toAbsolutePath()
        .normalize();

      if (!Files.exists(userFolder)) {
        Files.createDirectories(userFolder);
        LOGGER.info(FileConstant.DIRECTORY_CREATED + userFolder);
      }

      Files.deleteIfExists(
        Paths.get(
          userFolder +
          user.getUsername() +
          FileConstant.DOT +
          FileConstant.JPG_EXTENSION
        )
      );
      Files.copy(
        profileImage.getInputStream(),
        userFolder.resolve(
          user.getUsername() + FileConstant.DOT + FileConstant.JPG_EXTENSION
        ),
        java.nio.file.StandardCopyOption.REPLACE_EXISTING
      );

      user.setProfileImageUrl(setProfileImageUrl(user.getUsername()));
      userRepository.save(user);
      LOGGER.info(
        FileConstant.FILE_SAVE_IN_FILE_SYSTEM +
        profileImage.getOriginalFilename()
      );
    }
  }

  private String setProfileImageUrl(String username) {
    return ServletUriComponentsBuilder
      .fromCurrentContextPath()
      .path(
        FileConstant.USER_IMAGE_PATH +
        username +
        FileConstant.FORWARD_SLASH +
        username +
        FileConstant.DOT +
        FileConstant.JPG_EXTENSION
      )
      .toUriString();
  }

  private Role getRoleEnumName(String role) {
    return Role.valueOf(role.toUpperCase());
  }

  @Override
  public User updateUser(
    String currentUsername,
    String newFirstName,
    String newLastName,
    String newUsername,
    String newEmail,
    String role,
    boolean isNotLocked,
    boolean isActive,
    MultipartFile profileImage
  )
    throws UsernameNotFoundException, UsernameExistException, EmailExistException, IOException, NotAnImageFileException {
    User currentUser = validateNewUsernameAndEmail(
      currentUsername,
      newUsername,
      newEmail
    );

    currentUser.setFirstName(newFirstName);
    currentUser.setLastName(newLastName);
    currentUser.setUsername(newUsername);
    currentUser.setEmail(newEmail);
    currentUser.setActive(isActive);
    currentUser.setNotLocked(isNotLocked);
    currentUser.setRole(getRoleEnumName(role).name());
    currentUser.setAuthorities(getRoleEnumName(role).getAuthorities());
    userRepository.save(currentUser);
    saveProfileImage(currentUser, profileImage);

    return currentUser;
  }

  @Override
  public void deleteUser(String username) throws IOException {
    User user = userRepository.findUserByUsername(username);
    Path userFolder = Paths
      .get(FileConstant.USER_FOLDER + user.getUsername())
      .toAbsolutePath()
      .normalize();
    FileUtils.deleteDirectory(new File(userFolder.toString()));
    userRepository.deleteById(user.getId());
  }

  @Override
  public void resetPassword(String email)
    throws EmailNotFoundException, MessagingException {
    User user = userRepository.findUserByEmail(email);

    if (user == null) {
      throw new EmailNotFoundException(
        UserImplConstant.NO_USER_FOUND_BY_USERNAME + email
      );
    }
    String password = generatePassword();

    System.out.println("RESETED PASSWORD " + password);

    user.setPassword(encordedPassword(password));
    userRepository.save(user);
    emailService.sendNewPasswordEmail(
      user.getFirstName(),
      password,
      user.getEmail()
    );
  }

  @Override
  public User updateProfileImage(String username, MultipartFile profileImage)
    throws UsernameNotFoundException, UsernameExistException, EmailExistException, IOException, NotAnImageFileException {
    User user = validateNewUsernameAndEmail(username, null, null);
    saveProfileImage(user, profileImage);
    return user;
  }
}
