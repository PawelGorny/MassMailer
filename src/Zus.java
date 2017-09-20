
import javax.mail.MessagingException;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.util.Arrays;


public class Zus {
    private JTabbedPane tabs;
    private JPanel mainPanel;
    private JButton zapiszButton;
    private JTextField emailServer;
    private JTextField emailAddress;
    private JPasswordField emailPassword;
    private JTextArea emails;
    private JButton emailButton;
    private JProgressBar progressBar1;
    private JTextField emailSubject;
    private JTextField textFieldTo;
    private JTextArea body;
    private JTextArea bccProcessed;
    private JTextField textAtt;
    private JButton attAdd;
    private JComboBox comboBulk;
    private File file;

    public Zus() {
        readSettings();
        zapiszButton.addActionListener(e -> saveSettings());
        emailButton.addActionListener(e -> {
            emailButton.setEnabled(false);
            mainPanel.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            EmailSender sender = new EmailSender();
            sender.addPropertyChangeListener(
                    new PropertyChangeListener() {
                        public void propertyChange(PropertyChangeEvent evt) {
                            if ("progress".equals(evt.getPropertyName())) {
                                progressBar1.setValue((Integer) evt.getNewValue());
                            }
                        }
                    }
            );
            sender.execute();
        });

        attAdd.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                final JFileChooser fc = new JFileChooser();
                int returnVal = fc.showOpenDialog(mainPanel);
                if (returnVal == JFileChooser.APPROVE_OPTION) {
                    file = fc.getSelectedFile();
                    textAtt.setText(file.getAbsolutePath());
                }
            }
        });
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Mailer");
        frame.setContentPane(new Zus().mainPanel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setSize(800, 600);
        frame.setVisible(true);
    }

    public File getFile() {
        return file;
    }

    private void clearProcessed() {
        bccProcessed.setText("");
    }

    private void addProcessed(String[] processedEmail, String nr) {
        for (String email : processedEmail) {
            if (email != null && !email.trim().isEmpty()) {
                bccProcessed.append("[");
                bccProcessed.append(nr);
                bccProcessed.append("]");
                bccProcessed.append(email);
                bccProcessed.append("\n");
            }
        }
    }

    private void readSettings() {
        emailSubject.setText(Constants.EMAIL_SUBJECT);
        try {
            BufferedReader br = new BufferedReader(new FileReader(Constants.CONF_FILE));
            String line = br.readLine();
            int i = 0;
            while (line != null) {
                switch (i) {
                    case 0:
                        emailServer.setText(line);
                        break;
                    case 1:
                        emailAddress.setText(line);
                        break;
                    case 2: {
                        for (int c = 0; c < comboBulk.getItemCount(); c++) {
                            if (String.valueOf(c).equals(line)) {
                                comboBulk.setSelectedIndex(c);
                                break;
                            }
                        }
                        break;
                    }
                    default:
                        if (emails.getText() != null && !emails.getText().isEmpty()) {
                            emails.append("\n");
                        }
                        emails.append(line);
                }
                line = br.readLine();
                i++;
            }
        } catch (FileNotFoundException e) {
            setDefaultValues();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(mainPanel, e.getMessage(), "Problem z odczytem", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            setDefaultValues();
        }
    }

    private void setDefaultValues() {
        emailServer.setText(Constants.EMAIL_SERVER);
        emailAddress.setText(Constants.EMAIL_ADDRESS);
        emails.setText(Constants.EMAIL_ADDRESS);
        comboBulk.setSelectedIndex(2);
    }


    private boolean saveSettings() {

        EmailService emailService = new EmailService(emailServer.getText(), emailAddress.getText(), emailPassword.getPassword(), emailSubject.getText());
        String emailTest = emailService.test();
        if (emailTest != null) {
            JOptionPane.showMessageDialog(mainPanel, emailTest, "Problem z serwerem poczty", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        sortEmails();
        try {
            BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(Constants.CONF_FILE));
            bufferedWriter.write(emailServer.getText());
            bufferedWriter.newLine();
            bufferedWriter.write(emailAddress.getText());
            bufferedWriter.newLine();
            bufferedWriter.write(String.valueOf(comboBulk.getSelectedIndex()));
            bufferedWriter.newLine();
            bufferedWriter.write(emails.getText());
            bufferedWriter.flush();
            bufferedWriter.close();
            JOptionPane.showMessageDialog(mainPanel, "OK", "Zapis", JOptionPane.PLAIN_MESSAGE);
            return true;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(mainPanel, e.getMessage(), "Problem z zapisem", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
        return false;
    }

    private void sortEmails() {
        String[] recipients = emails.getText().split("\n");
        if (recipients.length > 0) {
            Arrays.sort(recipients);
            StringBuilder stringBuilder = new StringBuilder();
            for (String e : recipients) {
                stringBuilder.append(e).append("\n");
            }
            emails.setText(stringBuilder.toString());
        }
    }

    private int getBulkSize() {
        return Integer.valueOf(comboBulk.getItemAt(comboBulk.getSelectedIndex()).toString());
    }

    class EmailSender extends SwingWorker<Void, Void> {
        @Override
        protected Void doInBackground() throws Exception {
            clearProcessed();
            EmailService emailService = new EmailService(emailServer.getText(), emailAddress.getText(), emailPassword.getPassword(), emailSubject.getText());
            emailService.setBody(body.getText());
            if (getFile() != null) {
                emailService.setFile(getFile());
            }
            String[] recipients = emails.getText().split("\n");
            String[] recipientsToSend = new String[getBulkSize()];
            String to = textFieldTo.getText().trim();
            progressBar1.setValue(0);
            int emailNr = 0;
            int recipentNr = 0;
            int bulksNr = recipients.length / recipientsToSend.length
                    + (recipients.length / recipientsToSend.length > 0 && recipients.length % recipientsToSend.length > 0 ? 1 : 0);
            boolean unsent = false;
            for (String recipient : recipients) {
                recipient = recipient.trim();
                if (recipentNr < recipientsToSend.length) {
                    recipientsToSend[recipentNr++] = recipient;
                    unsent = true;
                    continue;
                }
                try {
                    emailService.send(to, recipientsToSend);
                } catch (MessagingException e) {
                    JOptionPane.showMessageDialog(mainPanel, e.getMessage(), "Problem z emailem", JOptionPane.ERROR_MESSAGE);
                    e.printStackTrace();
                }
                int progress = Math.min(++emailNr * (100 / bulksNr), 100);
                addProcessed(recipientsToSend, String.valueOf(emailNr));
                setProgress(progress);
                mainPanel.repaint();

                recipentNr = 0;
                recipientsToSend = new String[recipientsToSend.length];
                recipientsToSend[recipentNr++] = recipient;
                unsent = true;
            }
            if (unsent) {
                try {
                    emailService.send(to, recipientsToSend);
                    addProcessed(recipientsToSend, String.valueOf(++emailNr));
                } catch (MessagingException e) {
                    JOptionPane.showMessageDialog(mainPanel, e.getMessage(), "Problem z emailem", JOptionPane.ERROR_MESSAGE);
                    e.printStackTrace();
                }
            }
            setProgress(100);
            return null;
        }

        @Override
        protected void done() {
            Toolkit.getDefaultToolkit().beep();
            emailButton.setEnabled(true);
            mainPanel.setCursor(null);
        }
    }
}
