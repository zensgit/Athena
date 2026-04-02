import { AspectDefinition } from 'services/contentModelService';
import {
  buildAspectInitialPropertyValues,
  buildAspectPropertyPayload,
  getAspectPropertyListOptions,
} from './aspectPropertyFormUtils';

const titledAspect: AspectDefinition = {
  id: 'aspect-1',
  name: 'titled',
  title: 'Titled',
  description: 'Adds title metadata',
  parentName: null,
  qualifiedName: 'cm:titled',
  properties: [
    {
      id: 'prop-1',
      name: 'title',
      title: 'Title',
      description: 'Document title',
      dataType: 'TEXT',
      mandatory: true,
      multiValued: false,
      defaultValue: 'Untitled',
      indexed: true,
      protectedField: false,
      qualifiedName: 'cm:title',
      constraints: [],
    },
    {
      id: 'prop-2',
      name: 'published',
      title: 'Published',
      description: 'Published flag',
      dataType: 'BOOLEAN',
      mandatory: false,
      multiValued: false,
      defaultValue: 'false',
      indexed: true,
      protectedField: false,
      qualifiedName: 'cm:published',
      constraints: [],
    },
    {
      id: 'prop-3',
      name: 'rating',
      title: 'Rating',
      description: 'Numeric rating',
      dataType: 'INT',
      mandatory: false,
      multiValued: false,
      defaultValue: null,
      indexed: true,
      protectedField: false,
      qualifiedName: 'cm:rating',
      constraints: [],
    },
    {
      id: 'prop-4',
      name: 'categories',
      title: 'Categories',
      description: 'Category list',
      dataType: 'TEXT',
      mandatory: false,
      multiValued: true,
      defaultValue: null,
      indexed: true,
      protectedField: false,
      qualifiedName: 'cm:categories',
      constraints: [],
    },
    {
      id: 'prop-5',
      name: 'status',
      title: 'Status',
      description: 'Allowed status',
      dataType: 'TEXT',
      mandatory: false,
      multiValued: false,
      defaultValue: null,
      indexed: true,
      protectedField: false,
      qualifiedName: 'cm:status',
      constraints: [
        {
          id: 'constraint-1',
          constraintType: 'LIST',
          parameters: { values: ['draft', 'published'] },
        },
      ],
    },
  ],
};

describe('aspectPropertyFormUtils', () => {
  it('builds initial values from aspect defaults keyed by qualified name', () => {
    expect(buildAspectInitialPropertyValues(titledAspect)).toEqual({
      'cm:title': 'Untitled',
      'cm:published': 'false',
      'cm:rating': '',
      'cm:categories': '',
      'cm:status': '',
    });
  });

  it('builds typed payloads for boolean, numeric, and multi-valued properties', () => {
    expect(
      buildAspectPropertyPayload(titledAspect, {
        'cm:title': 'Quarterly Report',
        'cm:published': 'true',
        'cm:rating': '7',
        'cm:categories': 'finance,quarterly\n2026',
        'cm:status': 'draft',
      })
    ).toEqual({
      'cm:title': 'Quarterly Report',
      'cm:published': true,
      'cm:rating': 7,
      'cm:categories': ['finance', 'quarterly', '2026'],
      'cm:status': 'draft',
    });
  });

  it('extracts list constraint options from property definitions', () => {
    expect(getAspectPropertyListOptions(titledAspect.properties[4])).toEqual(['draft', 'published']);
  });
});
