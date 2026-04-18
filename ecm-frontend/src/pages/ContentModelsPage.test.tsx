import React from 'react';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { toast } from 'react-toastify';
import ContentModelsPage from './ContentModelsPage';
import contentModelService from 'services/contentModelService';
import dictionaryService from 'services/dictionaryService';

jest.mock('react-toastify', () => ({
  toast: {
    error: jest.fn(),
    success: jest.fn(),
    warn: jest.fn(),
  },
}));

jest.mock('services/contentModelService', () => ({
  __esModule: true,
  default: {
    listModels: jest.fn(),
    addPropertyToType: jest.fn(),
    addPropertyToAspect: jest.fn(),
    activateModel: jest.fn(),
    deactivateModel: jest.fn(),
    createModel: jest.fn(),
    addType: jest.fn(),
    addAspect: jest.fn(),
    updateType: jest.fn(),
    updateAspect: jest.fn(),
    deleteType: jest.fn(),
    deleteAspect: jest.fn(),
    addConstraint: jest.fn(),
    deleteProperty: jest.fn(),
    deleteConstraint: jest.fn(),
  },
}));

jest.mock('services/dictionaryService', () => ({
  __esModule: true,
  default: {
    listTypes: jest.fn(),
    listAspects: jest.fn(),
    getType: jest.fn(),
    getTypeHierarchy: jest.fn(),
    getMandatoryAspects: jest.fn(),
    getAspect: jest.fn(),
  },
}));

const mockedContentModelService = contentModelService as jest.Mocked<typeof contentModelService>;
const mockedDictionaryService = dictionaryService as jest.Mocked<typeof dictionaryService>;
const toastSuccessMock = toast.success as jest.Mock;

describe('ContentModelsPage encrypted property authoring', () => {
  beforeEach(() => {
    jest.clearAllMocks();

    mockedContentModelService.listModels.mockResolvedValue([
      {
        id: 'model-1',
        namespaceUri: 'urn:acme:model',
        prefix: 'acme',
        name: 'finance',
        description: 'Finance model',
        author: 'admin',
        status: 'DRAFT',
        versionLabel: '1.0',
        types: [
          {
            id: 'type-1',
            name: 'contract',
            qualifiedName: 'acme:contract',
            mandatoryAspects: [],
            properties: [],
          },
        ],
        aspects: [],
      },
    ] as any);

    mockedDictionaryService.listTypes.mockResolvedValue([
      {
        id: 'type-1',
        name: 'contract',
        qualifiedName: 'acme:contract',
        mandatoryAspects: [],
        properties: [],
      },
    ] as any);
    mockedDictionaryService.listAspects.mockResolvedValue([]);
    mockedDictionaryService.getType.mockResolvedValue({
      id: 'type-1',
      name: 'contract',
      qualifiedName: 'acme:contract',
      title: 'Contract',
      mandatoryAspects: [],
      properties: [
        {
          id: 'prop-existing',
          name: 'secretCode',
          qualifiedName: 'acme:secretCode',
          dataType: 'TEXT',
          mandatory: false,
          multiValued: false,
          indexed: false,
          protectedField: false,
          encrypted: true,
          constraints: [],
        },
      ],
    } as any);
    mockedDictionaryService.getTypeHierarchy.mockResolvedValue(['cm:content', 'acme:contract']);
    mockedDictionaryService.getMandatoryAspects.mockResolvedValue([]);
    mockedContentModelService.addPropertyToType.mockResolvedValue({
      id: 'prop-new',
      name: 'vaultId',
      qualifiedName: 'acme:vaultId',
      dataType: 'TEXT',
      mandatory: false,
      multiValued: false,
      indexed: false,
      protectedField: false,
      encrypted: true,
      constraints: [],
    } as any);
  });

  it('shows encrypted properties and sends the encrypted flag when adding one', async () => {
    render(<ContentModelsPage />);

    expect(await screen.findByText('secretCode')).toBeTruthy();
    expect(await screen.findByText('Encrypted')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Property' }));

    const dialog = await screen.findByRole('dialog', { name: /Add Property to acme:contract/i });
    const encryptedToggle = within(dialog).getByRole('button', { name: 'Encrypted: No' });
    fireEvent.click(encryptedToggle);
    expect(screen.getByText(/stored outside plaintext node properties/i)).toBeTruthy();

    fireEvent.change(within(dialog).getByRole('textbox', { name: 'Name' }), { target: { value: 'vaultId' } });
    fireEvent.change(within(dialog).getByRole('textbox', { name: 'Title' }), { target: { value: 'Vault ID' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Add' }));

    await waitFor(() => {
      expect(mockedContentModelService.addPropertyToType).toHaveBeenCalledWith('type-1', {
        name: 'vaultId',
        title: 'Vault ID',
        dataType: 'TEXT',
        mandatory: false,
        multiValued: false,
        encrypted: true,
        defaultValue: undefined,
      });
    });

    expect(toastSuccessMock).toHaveBeenCalledWith('Property added');
  });
});
