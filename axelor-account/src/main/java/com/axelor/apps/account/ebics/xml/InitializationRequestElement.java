/*
 * Copyright (c) 1990-2012 kopiLeft Development SARL, Bizerte, Tunisia
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License version 2.1 as published by the Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 * $Id$
 */

package com.axelor.apps.account.ebics.xml;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

import javax.crypto.Cipher;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import com.axelor.apps.account.ebics.schema.h003.EbicsRequestDocument;
import com.axelor.apps.account.ebics.schema.xmldsig.SignatureType;
import com.axelor.apps.account.ebics.client.EbicsSession;
import com.axelor.apps.account.ebics.client.OrderType;
import com.axelor.apps.account.ebics.client.EbicsUtils;
import com.axelor.apps.account.ebics.client.DefaultEbicsRootElement;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.IException;


/**
 * The <code>InitializationRequestElement</code> is the root element for
 * ebics uploads and downloads requests. The response of this element is
 * then used either to upload or download files from the ebics server.
 *
 * @author Hachani
 *
 */
public abstract class InitializationRequestElement extends DefaultEbicsRootElement {

  /**
   * Construct a new <code>InitializationRequestElement</code> root element.
   * @param session the current ebics session.
   * @param type the initialization type (UPLOAD, DOWNLOAD).
   * @param name the element name.
   * @throws EbicsException
   */
  public InitializationRequestElement(EbicsSession session, OrderType type, String name) throws AxelorException  {
    super(session);
    this.type = type;
    this.name = name;
    nonce = EbicsUtils.generateNonce();
  }

  @Override
  public void build() throws AxelorException {
    SignedInfo			signedInfo;

    buildInitialization();
    signedInfo = new SignedInfo(session.getUser(), getDigest());
    signedInfo.build();
    ((EbicsRequestDocument)document).getEbicsRequest().setAuthSignature((SignatureType) signedInfo.getSignatureType());
    ((EbicsRequestDocument)document).getEbicsRequest().getAuthSignature().setSignatureValue(EbicsXmlFactory.createSignatureValueType(signedInfo.sign(toByteArray())));
  }

  @Override
  public String getName() {
    return name + ".xml";
  }

  @Override
  public byte[] toByteArray() {
    setSaveSuggestedPrefixes("http://www.ebics.org/H003", "");

    return super.toByteArray();
  }

  /**
   * Returns the digest value of the authenticated XML portions.
   * @return  the digest value.
   * @throws EbicsException Failed to retrieve the digest value.
   */
  public byte[] getDigest() throws AxelorException {
    addNamespaceDecl("ds", "http://www.w3.org/2000/09/xmldsig#");

    try {
      return MessageDigest.getInstance("SHA-256", "BC").digest(EbicsUtils.canonize(toByteArray()));
    } catch (NoSuchAlgorithmException e) {
      throw new AxelorException(e.getMessage(), IException.CONFIGURATION_ERROR );
    } catch (NoSuchProviderException e) {
      throw new AxelorException(e.getMessage(), IException.CONFIGURATION_ERROR );
    }
  }

  /**
   * Returns the element type.
   * @return the element type.
   */
  public String getType() {
    return type.getOrderType();
  }

  /**
   * Decodes an hexadecimal input.
   * @param hex the hexadecimal input
   * @return the decoded hexadecimal value
   * @throws EbicsException
   */
  protected byte[] decodeHex(byte[] hex) throws AxelorException {
    if (hex == null) {
      throw new AxelorException("Bank digest is empty, HPB request must be performed before", IException.CONFIGURATION_ERROR);
    }

    try {
      return Hex.decodeHex((new String(hex)).toCharArray());
    } catch (DecoderException e) {
      throw new AxelorException(e.getMessage(), IException.CONFIGURATION_ERROR );
    }
  }

  /**
   * Generates the upload transaction key
   * @return the transaction key
   */
  protected byte[] generateTransactionKey() throws AxelorException {
    try {
      Cipher			cipher;

      cipher = Cipher.getInstance("RSA/NONE/PKCS1Padding", BouncyCastleProvider.PROVIDER_NAME);
      cipher.init(Cipher.ENCRYPT_MODE, session.getBankE002Key());

      return cipher.doFinal(nonce);
    } catch (Exception e) {
      e.printStackTrace();
      throw new AxelorException(e.getMessage(), IException.CONFIGURATION_ERROR );
    }
  }

  /**
   * Builds the initialization request according to the
   * element type.
   * @throws EbicsException build fails
   */
  public abstract void buildInitialization() throws AxelorException;

  // --------------------------------------------------------------------
  // DATA MEMBERS
  // --------------------------------------------------------------------

  private String			name;
  protected OrderType			type;
  protected byte[]			nonce;
  private static final long 		serialVersionUID = 8983807819242699280L;
}